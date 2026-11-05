/**
 * Logica pura portata dal Kotlin: i casi di DateCalculationsTest.kt e ReminderSchedulingTest.kt,
 * piu stato dei check-in, fuso orario e allowlist.
 */
import { describe, expect, it } from 'vitest';
import { checkInStatus, coerceGlobalThreshold, compareUrgency, DEFAULT_THRESHOLD, thresholdFor } from '../src/domain/checkins.js';
import {
  ageOnNextBirthday,
  currentAge,
  daysSince,
  daysUntilBirthday,
  formatInstant,
  formatLocalDate,
  fromEpochDay,
  localDateOf,
  nextBirthdayOccurrence,
  parseLocalDate,
  signedDaysFromToday,
  toEpochDay,
  zonedEpoch,
  type LocalDate,
} from '../src/domain/dates.js';
import {
  completionDay,
  completionInstant,
  latestCompletion,
  MIN_INTERVAL_DAYS,
  nextFireAt,
  nextIntervalDays,
  presetByKey,
  scheduleAfterHistoryChange,
  window,
  type RandomInt,
} from '../src/domain/reminders.js';
import { isEmailAllowed } from '../src/config.js';

const d = (year: number, month: number, day: number): LocalDate => ({ year, month, day });
const iso = (x: LocalDate) => formatLocalDate(x);

/** Generatore deterministico (mulberry32) con la forma di Random.nextInt(from, until). */
function seeded(seed: number): RandomInt {
  let a = seed >>> 0;
  return (from, until) => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    const r = ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    return from + Math.floor(r * (until - from));
  };
}

describe('DateCalculations (da DateCalculationsTest.kt)', () => {
  it('next birthday occurrence is later this year when it has not passed', () => {
    expect(iso(nextBirthdayOccurrence(d(1990, 10, 12), d(2026, 6, 10)))).toBe('2026-10-12');
  });

  it('next birthday occurrence rolls to next year when it already passed', () => {
    expect(iso(nextBirthdayOccurrence(d(1990, 10, 12), d(2026, 11, 1)))).toBe('2027-10-12');
  });

  it('birthday today counts as zero days away', () => {
    expect(daysUntilBirthday(d(1990, 10, 12), d(2026, 10, 12))).toBe(0);
  });

  it('feb 29 birthday falls back to feb 28 in a non-leap year', () => {
    expect(iso(nextBirthdayOccurrence(d(2000, 2, 29), d(2026, 1, 1)))).toBe('2026-02-28');
  });

  it('feb 29 birthday stays on feb 29 in a leap year, and rolls correctly after feb 28', () => {
    expect(iso(nextBirthdayOccurrence(d(2000, 2, 29), d(2028, 1, 1)))).toBe('2028-02-29');
    expect(iso(nextBirthdayOccurrence(d(2000, 2, 29), d(2027, 3, 1)))).toBe('2028-02-29');
    expect(daysUntilBirthday(d(2000, 2, 29), d(2026, 2, 28))).toBe(0);
  });

  it('days since counts whole days and never goes negative', () => {
    const past = Date.parse('2026-06-01T08:00:00Z');
    const now = Date.parse('2026-06-10T09:00:00Z');
    expect(daysSince(past, now, 'UTC')).toBe(9);
    expect(daysSince(now, past, 'UTC')).toBe(0);
  });

  it('days since uses calendar days in the user time zone', () => {
    // 23:30 a Roma del 1 giugno e 00:30 del 2 giugno: un giorno di calendario, anche se passa un'ora
    const late = Date.parse('2026-06-01T21:30:00Z');
    const early = Date.parse('2026-06-01T22:30:00Z');
    expect(daysSince(late, early, 'Europe/Rome')).toBe(1);
    expect(daysSince(late, early, 'UTC')).toBe(0);
  });

  it('age on next birthday is computed from the birth year', () => {
    expect(ageOnNextBirthday(d(1990, 10, 12), d(2026, 6, 10))).toBe(36);
  });

  it('age is null when the birth year is unset', () => {
    expect(ageOnNextBirthday(d(1, 10, 12), d(2026, 6, 10))).toBeNull();
    expect(currentAge(d(1, 10, 12), d(2026, 6, 10))).toBeNull();
  });

  it('current age follows Period.between (feb 29 turns a year older on mar 1 in non-leap years)', () => {
    expect(currentAge(d(1990, 10, 12), d(2026, 10, 11))).toBe(35);
    expect(currentAge(d(1990, 10, 12), d(2026, 10, 12))).toBe(36);
    expect(currentAge(d(2000, 2, 29), d(2001, 2, 28))).toBe(0);
    expect(currentAge(d(2000, 2, 29), d(2001, 3, 1))).toBe(1);
  });

  it('signed days from today is negative for the past and positive for the future', () => {
    expect(signedDaysFromToday(d(2026, 6, 5), d(2026, 6, 10))).toBe(-5);
    expect(signedDaysFromToday(d(2026, 6, 14), d(2026, 6, 10))).toBe(4);
  });
});

describe('date locali e fuso', () => {
  it('parse rifiuta date impossibili e accetta il 29 febbraio solo nei bisestili', () => {
    expect(parseLocalDate('2024-02-29')).toEqual(d(2024, 2, 29));
    expect(parseLocalDate('2026-02-29')).toBeNull();
    expect(parseLocalDate('2026-13-01')).toBeNull();
    expect(parseLocalDate('26-01-01')).toBeNull();
    expect(parseLocalDate(null)).toBeNull();
  });

  it('epoch day come LocalDate.toEpochDay', () => {
    expect(toEpochDay(d(1970, 1, 1))).toBe(0);
    expect(toEpochDay(d(2026, 11, 2))).toBe(20759);
    expect(iso(fromEpochDay(20759))).toBe('2026-11-02');
    expect(toEpochDay(d(1969, 12, 31))).toBe(-1);
  });

  it('mezzogiorno a Roma con e senza ora legale', () => {
    expect(new Date(zonedEpoch(d(2026, 7, 15), 12, 0, 'Europe/Rome')).toISOString()).toBe('2026-07-15T10:00:00.000Z');
    expect(new Date(zonedEpoch(d(2026, 1, 15), 12, 0, 'Europe/Rome')).toISOString()).toBe('2026-01-15T11:00:00.000Z');
    expect(formatInstant(Date.parse('2026-01-15T11:00:00Z'), 'Europe/Rome')).toBe('2026-01-15T12:00:00+01:00');
    expect(formatInstant(Date.parse('2026-07-15T10:00:00Z'), 'Europe/Rome')).toBe('2026-07-15T12:00:00+02:00');
  });

  it('il giorno locale dipende dal fuso', () => {
    const ms = Date.parse('2026-11-01T23:30:00Z');
    expect(iso(localDateOf(ms, 'Europe/Rome'))).toBe('2026-11-02');
    expect(iso(localDateOf(ms, 'UTC'))).toBe('2026-11-01');
  });

  it('il giorno del cambio d\'ora resta giusto', () => {
    const ms = zonedEpoch(d(2026, 3, 29), 12, 0, 'Europe/Rome');
    expect(formatInstant(ms, 'Europe/Rome')).toBe('2026-03-29T12:00:00+02:00');
  });
});

describe('ReminderScheduling (da ReminderSchedulingTest.kt)', () => {
  it('window is centred on the target and sized by the jitter', () => {
    expect(window(14, 20)).toEqual({ low: 11, high: 17 });
  });

  it('zero jitter yields a fixed one-day window at the target', () => {
    expect(window(30, 0)).toEqual({ low: 30, high: 30 });
  });

  it('window never dips below one day', () => {
    const w = window(1, 90);
    expect(w.low).toBeGreaterThanOrEqual(MIN_INTERVAL_DAYS);
    expect(w.high).toBeGreaterThanOrEqual(w.low);
  });

  it('out-of-range inputs are clamped', () => {
    expect(window(-5, 1000).low).toBe(MIN_INTERVAL_DAYS);
  });

  it('drawn intervals always fall inside the window and actually vary', () => {
    const random = seeded(42);
    const w = window(14, 20);
    const draws = Array.from({ length: 200 }, () => nextIntervalDays(14, 20, random));
    expect(draws.every((x) => x >= w.low && x <= w.high)).toBe(true);
    expect(new Set(draws).size).toBeGreaterThan(1);
  });

  it('next fire adds the drawn whole-day interval to the base instant', () => {
    const base = Date.parse('2026-07-20T09:00:00Z');
    const expectedDays = nextIntervalDays(14, 20, seeded(7));
    const next = nextFireAt(base, 14, 20, seeded(7));
    expect(next).toBe(base + expectedDays * 86_400_000);
    expect(next).toBeGreaterThan(base);
  });

  it('i preset hanno le manopole dell app', () => {
    expect(presetByKey('love_new_activity')).toMatchObject({ category: 'LOVE', targetIntervalDays: 30, jitterPercent: 30 });
    expect(presetByKey('family_siblings')).toMatchObject({ targetIntervalDays: 21, jitterPercent: 20 });
    expect(presetByKey('nope')).toBeUndefined();
  });
});

describe('storico dei completamenti e ripianificazione', () => {
  const DAY = 86_400_000;
  const low: RandomInt = (from) => from; // estremo basso della finestra: 14/0 -> 14 giorni
  const noon = (epochDay: number) => zonedEpoch(fromEpochDay(epochDay), 12, 0, 'Europe/Rome');
  const T = Date.parse('2026-10-01T08:00:00Z');
  const base = { targetIntervalDays: 14, jitterPercent: 0, nowMs: Date.parse('2026-11-02T10:00:00Z'), random: low };

  it('giorno e istante come DocumentMapper: day, poi at / millis al giorno; solo il giorno = mezzogiorno locale', () => {
    expect(completionDay({ day: 20759, at: 0 })).toBe(20759);
    expect(completionDay({ at: Date.parse('2026-11-02T23:30:00Z') })).toBe(20759);
    expect(completionDay({})).toBeNull();
    expect(completionInstant({ day: 20759, at: null }, noon)).toBe(Date.parse('2026-11-02T11:00:00Z'));
    expect(latestCompletion([{ day: 20759, at: null }, { day: 20758, at: Date.parse('2026-11-01T20:00:00Z') }], noon)).toBe(Date.parse('2026-11-02T11:00:00Z'));
    expect(latestCompletion([], noon)).toBeNull();
  });

  it('ultimo completamento invariato (aggiunta o cancellazione di uno piu vecchio): nessuna ripianificazione', () => {
    expect(scheduleAfterHistoryChange({ ...base, latestBefore: T, latestAfter: T })).toBeNull();
    expect(scheduleAfterHistoryChange({ ...base, latestBefore: null, latestAfter: null })).toBeNull();
  });

  it('nuovo ultimo completamento: nextFireAt da li con intervallo fresco, lastFiredAt non si tocca', () => {
    const at = T + 5 * DAY;
    expect(scheduleAfterHistoryChange({ ...base, latestBefore: null, latestAfter: at })).toEqual({ nextFireAt: at + 14 * DAY });
    expect(scheduleAfterHistoryChange({ ...base, latestBefore: T, latestAfter: at })).toEqual({ nextFireAt: at + 14 * DAY });
  });

  it('ultimo tolto o spostato indietro: si riparte dal piu recente rimasto, anche se la data cade nel passato', () => {
    const prev = T - 20 * DAY;
    const r = scheduleAfterHistoryChange({ ...base, latestBefore: T, latestAfter: prev });
    expect(r).toEqual({ nextFireAt: prev + 14 * DAY });
    expect(r!.nextFireAt).toBeLessThan(base.nowMs);
  });

  it('storico svuotato: lastFiredAt null e prossima data da adesso', () => {
    expect(scheduleAfterHistoryChange({ ...base, latestBefore: T, latestAfter: null })).toEqual({ lastFiredAt: null, nextFireAt: base.nowMs + 14 * DAY });
  });
});

describe('stato dei check-in (CheckInStatus.of)', () => {
  it('soglie: FRESH sotto warning, DUE fino a critical, OVERDUE oltre, NEVER senza check-in', () => {
    expect(checkInStatus(null, DEFAULT_THRESHOLD)).toBe('NEVER');
    expect(checkInStatus(13, DEFAULT_THRESHOLD)).toBe('FRESH');
    expect(checkInStatus(14, DEFAULT_THRESHOLD)).toBe('DUE');
    expect(checkInStatus(29, DEFAULT_THRESHOLD)).toBe('DUE');
    expect(checkInStatus(30, DEFAULT_THRESHOLD)).toBe('OVERDUE');
  });

  it('soglia della persona solo con entrambi i valori', () => {
    expect(thresholdFor({ warningDays: 3, criticalDays: 7 }, DEFAULT_THRESHOLD)).toEqual({ warningDays: 3, criticalDays: 7 });
    expect(thresholdFor({ warningDays: 3, criticalDays: null }, DEFAULT_THRESHOLD)).toEqual(DEFAULT_THRESHOLD);
  });

  it('soglie globali coerce come UpdateDefaultThresholdUseCase', () => {
    expect(coerceGlobalThreshold(0, 0)).toEqual({ warningDays: 1, criticalDays: 2 });
    expect(coerceGlobalThreshold(20, 10)).toEqual({ warningDays: 20, criticalDays: 21 });
  });

  it('ordine di urgenza: NEVER, poi OVERDUE, poi DUE; a parita piu giorni prima', () => {
    const rows = [
      { status: 'DUE' as const, daysSince: 20 },
      { status: 'OVERDUE' as const, daysSince: 40 },
      { status: 'NEVER' as const, daysSince: null },
      { status: 'OVERDUE' as const, daysSince: 90 },
    ];
    expect(rows.sort(compareUrgency).map((r) => `${r.status}:${r.daysSince}`)).toEqual(['NEVER:null', 'OVERDUE:90', 'OVERDUE:40', 'DUE:20']);
  });
});

describe('allowlist', () => {
  it('solo le email in elenco, il jolly * non vale', () => {
    expect(isEmailAllowed('andrew.ferro04@gmail.com', 'Andrew.Ferro04@gmail.com')).toBe(true);
    expect(isEmailAllowed('andrew.ferro04@gmail.com', 'other@gmail.com')).toBe(false);
    expect(isEmailAllowed('*', 'other@gmail.com')).toBe(false);
    expect(isEmailAllowed(undefined, 'andrew.ferro04@gmail.com')).toBe(true);
  });
});
