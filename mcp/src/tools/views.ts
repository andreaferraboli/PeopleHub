/**
 * Viste dei documenti per i tool (date ISO nel fuso dell'utente) e regole condivise fra i tool.
 */
import { checkInStatus, coerceGlobalThreshold, DEFAULT_THRESHOLD, thresholdFor, type CheckInStatus, type Threshold } from '../domain/checkins.js';
import {
  ageOnNextBirthday,
  currentAge,
  daysSince,
  daysUntilBirthday,
  DEFAULT_TIME_ZONE,
  formatInstant,
  formatLocalDate,
  fromEpochDay,
  isValidTimeZone,
  localDateOf,
  nextBirthdayOccurrence,
  parseLocalDate,
  signedDaysFromToday,
} from '../domain/dates.js';
import { ToolError } from '../domain/errors.js';
import { toReminderCategory } from '../domain/reminders.js';
import type { Doc, SyncBatch } from '../store.js';
import type { Clock, ToolContext } from './helpers.js';

export const DEFAULT_BIRTHDAY_OFFSETS = [0, 1, 7];
export const ALLOWED_BIRTHDAY_OFFSETS = [0, 1, 3, 7, 30];
export const DEFAULT_DAILY_REMINDER_HOUR = 5;

export const num = (v: unknown): number | null => (typeof v === 'number' && Number.isFinite(v) ? v : null);
export const str = (v: unknown): string => (typeof v === 'string' ? v : '');
export const strOrNull = (v: unknown): string | null => (typeof v === 'string' && v.length > 0 ? v : null);
export const strArray = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : []);

export interface Interest {
  key: string;
  value: string;
}

export function interestsOf(v: unknown): Interest[] {
  if (!Array.isArray(v)) return [];
  return v
    .filter((x): x is Record<string, unknown> => !!x && typeof x === 'object')
    .map((x) => ({ key: str(x.key), value: str(x.value) }));
}

export function fullName(p: Doc): string {
  return `${str(p.firstName)} ${str(p.lastName)}`.trim();
}

/* -------------------------------------------------------------- impostazioni */

export interface Settings {
  warningDays: number;
  criticalDays: number;
  birthdayOffsets: number[];
  dailyReminderHour: number;
  timeZone: string;
}

export function settingsView(raw: Record<string, unknown>): Settings {
  const t = coerceGlobalThreshold(num(raw.warningDays) ?? DEFAULT_THRESHOLD.warningDays, num(raw.criticalDays) ?? DEFAULT_THRESHOLD.criticalDays);
  const offsets = Array.isArray(raw.birthdayOffsets) ? raw.birthdayOffsets.filter((x): x is number => typeof x === 'number') : DEFAULT_BIRTHDAY_OFFSETS;
  const hour = num(raw.dailyReminderHour);
  return {
    warningDays: t.warningDays,
    criticalDays: t.criticalDays,
    birthdayOffsets: [...new Set(offsets)].sort((a, b) => a - b),
    dailyReminderHour: hour !== null && hour >= 0 && hour <= 23 ? hour : DEFAULT_DAILY_REMINDER_HOUR,
    timeZone: isValidTimeZone(raw.timeZone) ? raw.timeZone : DEFAULT_TIME_ZONE,
  };
}

export async function loadSettings(ctx: ToolContext): Promise<Settings> {
  return settingsView(await ctx.store.settings());
}

export const globalThreshold = (s: Settings): Threshold => ({ warningDays: s.warningDays, criticalDays: s.criticalDays });

/* -------------------------------------------------------------- check-in */

/** Ultimo check-in vivo per persona (millis). */
export function lastCheckInByPerson(checkIns: Doc[]): Map<string, number> {
  const out = new Map<string, number>();
  for (const c of checkIns) {
    const pid = str(c.personId);
    const at = num(c.at);
    if (!pid || at === null) continue;
    if (!out.has(pid) || at > (out.get(pid) as number)) out.set(pid, at);
  }
  return out;
}

/** Ultimo check-in: dai check-in sincronizzati; se la persona non ne ha, la copia informativa lastCheckInAt. */
export function lastSeenMs(person: Doc, byPerson: Map<string, number>): number | null {
  return byPerson.get(person.id) ?? num(person.lastCheckInAt);
}

/** La persona e' seguita dal tracker di frequenza? (no famiglia, no check-in disattivati, no solo compleanno) */
export function isTracked(p: Doc): boolean {
  return p.isFamily !== true && p.checkInDisabled !== true && p.birthdayOnly !== true;
}

/* -------------------------------------------------------------- persone */

export interface BirthdayInfo {
  birthday: string;
  nextOccurrence: string;
  daysUntil: number;
  turningAge: number | null;
  currentAge: number | null;
}

export function birthdayInfo(raw: unknown, clk: Clock): BirthdayInfo | null {
  const b = parseLocalDate(raw);
  if (!b) return null;
  return {
    birthday: formatLocalDate(b),
    nextOccurrence: formatLocalDate(nextBirthdayOccurrence(b, clk.today)),
    daysUntil: daysUntilBirthday(b, clk.today),
    turningAge: ageOnNextBirthday(b, clk.today),
    currentAge: currentAge(b, clk.today),
  };
}

export interface PersonRow {
  id: string;
  name: string;
  firstName: string;
  lastName: string;
  birthday: string | null;
  daysUntilBirthday: number | null;
  tags: string[];
  isFamily: boolean;
  birthdayOnly: boolean;
  checkInDisabled: boolean;
  notificationsEnabled: boolean;
  lastCheckIn: string | null;
  daysSinceLastCheckIn: number | null;
  /** null per chi non e' seguito dal tracker (famiglia, check-in disattivati, solo compleanno). */
  checkInStatus: CheckInStatus | null;
  threshold: Threshold & { custom: boolean };
}

export function personRow(p: Doc, lastMs: number | null, clk: Clock, settings: Settings): PersonRow {
  const threshold = thresholdFor(p, globalThreshold(settings));
  const custom = num(p.warningDays) !== null && num(p.criticalDays) !== null;
  const since = lastMs === null ? null : daysSince(lastMs, clk.nowMs, clk.tz);
  const b = birthdayInfo(p.birthday, clk);
  return {
    id: p.id,
    name: fullName(p),
    firstName: str(p.firstName),
    lastName: str(p.lastName),
    birthday: b?.birthday ?? null,
    daysUntilBirthday: b?.daysUntil ?? null,
    tags: strArray(p.tags),
    isFamily: p.isFamily === true,
    birthdayOnly: p.birthdayOnly === true,
    checkInDisabled: p.checkInDisabled === true,
    notificationsEnabled: p.notificationsEnabled === true,
    lastCheckIn: lastMs === null ? null : formatLocalDate(localDateOf(lastMs, clk.tz)),
    daysSinceLastCheckIn: since,
    checkInStatus: isTracked(p) ? checkInStatus(since, threshold) : null,
    threshold: { ...threshold, custom },
  };
}

/** Persona viva per id, oppure errore chiaro. */
export async function requirePerson(ctx: ToolContext, id: string): Promise<Doc> {
  const p = await ctx.store.get('people', id);
  if (!p) throw new ToolError(`Person not found: ${id} (use list_people to get ids)`);
  return p;
}

/* -------------------------------------------------------------- check-in e uscite */

export function checkInView(c: Doc, tz: string) {
  const at = num(c.at) ?? 0;
  return { id: c.id, personId: str(c.personId), date: formatLocalDate(localDateOf(at, tz)), at: formatInstant(at, tz), note: strOrNull(c.note), outingId: str(c.outingId) };
}

/** Uscita = check-in che condividono outingId. Data, ora e nota dal primo check-in (sono uguali per costruzione). */
export function groupOutings(checkIns: Doc[], people: Map<string, Doc>, tz: string) {
  const groups = new Map<string, Doc[]>();
  for (const c of checkIns) {
    const key = str(c.outingId) || `single:${c.id}`;
    const list = groups.get(key) ?? [];
    list.push(c);
    groups.set(key, list);
  }
  return [...groups.entries()]
    .map(([outingId, rows]) => {
      const at = Math.max(...rows.map((r) => num(r.at) ?? 0));
      return {
        outingId: outingId.startsWith('single:') ? null : outingId,
        date: formatLocalDate(localDateOf(at, tz)),
        at: formatInstant(at, tz),
        atMs: at,
        note: rows.map((r) => strOrNull(r.note)).find((n) => n !== null) ?? null,
        attendees: rows
          .map((r) => {
            const p = people.get(str(r.personId));
            return { checkInId: r.id, personId: str(r.personId), name: p ? fullName(p) : '(deleted person)' };
          })
          .sort((a, b) => a.name.localeCompare(b.name, 'it', { sensitivity: 'base' })),
      };
    })
    .sort((a, b) => b.atMs - a.atMs);
}

/**
 * Ricalcola lastCheckInAt (copia informativa) dei `personIds` sullo stato DOPO le modifiche (refreshLastCheckIn)
 * e aggiunge al lotto l'aggiornamento delle persone il cui valore cambia.
 */
export function refreshLastCheckIn(batch: SyncBatch, people: Doc[], checkInsAfter: Doc[], personIds: Iterable<string>): void {
  const latest = lastCheckInByPerson(checkInsAfter);
  for (const pid of new Set(personIds)) {
    const person = people.find((p) => p.id === pid);
    if (!person) continue;
    const value = latest.get(pid) ?? null;
    if ((num(person.lastCheckInAt) ?? null) !== value) batch.update('people', pid, { lastCheckInAt: value });
  }
}

/* -------------------------------------------------------------- eventi e promemoria */

export function eventView(e: Doc, clk: Clock, people: Map<string, Doc>) {
  const at = num(e.at) ?? 0;
  const day = localDateOf(at, clk.tz);
  const pid = strOrNull(e.personId);
  const person = pid ? people.get(pid) : undefined;
  return {
    id: e.id,
    title: str(e.title),
    date: formatLocalDate(day),
    at: formatInstant(at, clk.tz),
    daysFromToday: signedDaysFromToday(day, clk.today),
    description: strOrNull(e.description),
    category: strOrNull(e.category),
    personId: pid,
    personName: person ? fullName(person) : null,
  };
}

export function reminderView(r: Doc, clk: Clock, people: Map<string, Doc>, completions: Doc[]) {
  const next = num(r.nextFireAt) ?? 0;
  const last = num(r.lastFiredAt);
  const mine = completions.filter((c) => str(c.reminderId) === r.id);
  const lastDoneDay = mine.reduce<number | null>((acc, c) => (num(c.day) !== null && (acc === null || (c.day as number) > acc) ? (c.day as number) : acc), null);
  const person = people.get(str(r.personId));
  const nextDay = localDateOf(next, clk.tz);
  return {
    id: r.id,
    personId: str(r.personId),
    personName: person ? fullName(person) : null,
    title: str(r.title),
    note: strOrNull(r.note),
    category: toReminderCategory(r.category),
    targetIntervalDays: num(r.targetIntervalDays),
    jitterPercent: num(r.jitterPercent),
    enabled: r.enabled !== false,
    nextFireDate: formatLocalDate(nextDay),
    nextFireAt: formatInstant(next, clk.tz),
    daysUntilNext: signedDaysFromToday(nextDay, clk.today),
    lastFiredAt: last === null ? null : formatInstant(last, clk.tz),
    presetKey: strOrNull(r.presetKey),
    timesDone: mine.length,
    lastDoneOn: lastDoneDay === null ? null : formatLocalDate(fromEpochDay(lastDoneDay)),
  };
}

export function byId(docs: Doc[]): Map<string, Doc> {
  return new Map(docs.map((d) => [d.id, d]));
}
