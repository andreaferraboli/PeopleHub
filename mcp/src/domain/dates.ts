/**
 * Date pure, portate da core/domain (DateCalculations.kt) e dalle regole del fuso.
 * Una data locale e' una stringa ISO "YYYY-MM-DD" (lo stesso formato di `birthday` in Room/Firestore:
 * DateTimeFormatter.ISO_LOCAL_DATE). Gli istanti sono epoch millis. "Oggi" e "adesso" arrivano sempre da fuori.
 */
import { ToolError } from './errors.js';

export const DEFAULT_TIME_ZONE = 'Europe/Rome';
const DAY_MS = 86_400_000;
/** Anno di nascita senza significato (Kotlin: MIN_MEANINGFUL_YEAR = 1): eta' sconosciuta. */
const MIN_MEANINGFUL_YEAR = 1;

export interface LocalDate {
  year: number;
  month: number;
  day: number;
}

const pad = (n: number, w = 2) => String(n).padStart(w, '0');

export function isLeapYear(year: number): boolean {
  return (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
}

function daysInMonth(year: number, month: number): number {
  return [31, isLeapYear(year) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1];
}

/** "YYYY-MM-DD" valida (anche il 29 febbraio solo negli anni bisestili). null se non valida. */
export function parseLocalDate(text: unknown): LocalDate | null {
  if (typeof text !== 'string') return null;
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text.trim());
  if (!m) return null;
  const [year, month, day] = [Number(m[1]), Number(m[2]), Number(m[3])];
  if (month < 1 || month > 12 || day < 1 || day > daysInMonth(year, month)) return null;
  return { year, month, day };
}

export function requireLocalDate(text: string, label = 'data'): LocalDate {
  const d = parseLocalDate(text);
  if (!d) throw new ToolError(`${label} non valida: serve una data reale nel formato YYYY-MM-DD`);
  return d;
}

export function formatLocalDate(d: LocalDate): string {
  return `${pad(d.year, 4)}-${pad(d.month)}-${pad(d.day)}`;
}

/** Giorni dal 1970-01-01 (come LocalDate.toEpochDay). */
export function toEpochDay(d: LocalDate): number {
  const t = new Date(0);
  t.setUTCFullYear(d.year, d.month - 1, d.day);
  return Math.round(t.getTime() / DAY_MS);
}

export function fromEpochDay(epochDay: number): LocalDate {
  const t = new Date(epochDay * DAY_MS);
  return { year: t.getUTCFullYear(), month: t.getUTCMonth() + 1, day: t.getUTCDate() };
}

export function addDays(d: LocalDate, days: number): LocalDate {
  return fromEpochDay(toEpochDay(d) + days);
}

/** Giorni da `from` a `to` (negativo se `to` e' prima). */
export function daysBetween(from: LocalDate, to: LocalDate): number {
  return toEpochDay(to) - toEpochDay(from);
}

/** Fuso IANA valido? */
export function isValidTimeZone(tz: unknown): tz is string {
  if (typeof tz !== 'string' || !tz) return false;
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: tz });
    return true;
  } catch {
    return false;
  }
}

const formatters = new Map<string, Intl.DateTimeFormat>();
function partsIn(ms: number, tz: string) {
  let f = formatters.get(tz);
  if (!f) {
    f = new Intl.DateTimeFormat('en-US', { timeZone: tz, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' });
    formatters.set(tz, f);
  }
  const p = Object.fromEntries(f.formatToParts(new Date(ms)).map((x) => [x.type, x.value]));
  return { year: Number(p.year), month: Number(p.month), day: Number(p.day), hour: Number(p.hour) % 24, minute: Number(p.minute), second: Number(p.second) };
}

/** Scarto del fuso in quell'istante (millis da aggiungere all'UTC per avere l'ora locale). */
function offsetMs(ms: number, tz: string): number {
  const p = partsIn(ms, tz);
  const asUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
  return asUtc - Math.floor(ms / 1000) * 1000;
}

/** Data locale di un istante nel fuso. */
export function localDateOf(ms: number, tz: string): LocalDate {
  const p = partsIn(ms, tz);
  return { year: p.year, month: p.month, day: p.day };
}

/** Istante dell'ora locale `hour:minute` del giorno `d` nel fuso (con l'ora legale gestita). */
export function zonedEpoch(d: LocalDate, hour: number, minute: number, tz: string): number {
  const wall = Date.UTC(d.year, d.month - 1, d.day, hour, minute);
  let guess = wall - offsetMs(wall, tz);
  const second = wall - offsetMs(guess, tz);
  if (second !== guess) guess = second;
  return guess;
}

/** Istante in ISO 8601 con lo scarto del fuso, es. 2026-11-02T12:00:00+01:00. */
export function formatInstant(ms: number, tz: string): string {
  const p = partsIn(ms, tz);
  const off = Math.round(offsetMs(ms, tz) / 60_000);
  const sign = off >= 0 ? '+' : '-';
  const abs = Math.abs(off);
  return `${pad(p.year, 4)}-${pad(p.month)}-${pad(p.day)}T${pad(p.hour)}:${pad(p.minute)}:${pad(p.second)}${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`;
}

/* ------------------------------------------------- DateCalculations.kt */

/** Giorni interi passati da `pastMs` a `nowMs` nel fuso, mai negativi (daysSince). */
export function daysSince(pastMs: number, nowMs: number, tz: string): number {
  return Math.max(0, daysBetween(localDateOf(pastMs, tz), localDateOf(nowMs, tz)));
}

/** Il compleanno spostato a `year`: il 29 febbraio diventa 28 negli anni non bisestili (safeWithYear). */
function safeWithYear(birthday: LocalDate, year: number): LocalDate {
  if (birthday.month === 2 && birthday.day === 29 && !isLeapYear(year)) return { year, month: 2, day: 28 };
  return { year, month: birthday.month, day: birthday.day };
}

/** Prossima ricorrenza del compleanno oggi o dopo (nextBirthdayOccurrence). */
export function nextBirthdayOccurrence(birthday: LocalDate, today: LocalDate): LocalDate {
  const candidate = safeWithYear(birthday, today.year);
  return toEpochDay(candidate) < toEpochDay(today) ? safeWithYear(birthday, today.year + 1) : candidate;
}

/** Giorni al prossimo compleanno (0 = oggi). */
export function daysUntilBirthday(birthday: LocalDate, today: LocalDate): number {
  return daysBetween(today, nextBirthdayOccurrence(birthday, today));
}

/** Eta' compiuta al prossimo compleanno, null se l'anno non ha significato. */
export function ageOnNextBirthday(birthday: LocalDate, today: LocalDate): number | null {
  if (birthday.year <= MIN_MEANINGFUL_YEAR) return null;
  return nextBirthdayOccurrence(birthday, today).year - birthday.year;
}

/** Eta' attuale in anni interi (Period.between), null se l'anno non ha significato. */
export function currentAge(birthday: LocalDate, today: LocalDate): number | null {
  if (birthday.year <= MIN_MEANINGFUL_YEAR) return null;
  let age = today.year - birthday.year;
  if (today.month < birthday.month || (today.month === birthday.month && today.day < birthday.day)) age--;
  return age;
}

/** Giorni fra `today` e `date`: negativo nel passato, positivo nel futuro. */
export function signedDaysFromToday(date: LocalDate, today: LocalDate): number {
  return daysBetween(today, date);
}
