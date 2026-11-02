/**
 * Cadenza irregolare dei promemoria, portata da core/domain (ReminderScheduling.kt) e dai preset
 * (ReminderPresets.kt). Il caso e' iniettato (mai preso da dentro) per restare testabile.
 */

export const MIN_INTERVAL_DAYS = 1;
export const MAX_JITTER_PERCENT = 90;
export const DEFAULT_JITTER_PERCENT = 20;
const DAY_MS = 86_400_000;

/** Generatore di interi in [from, until): stessa forma di kotlin.random.Random.nextInt(from, until). */
export type RandomInt = (from: number, until: number) => number;

export const mathRandomInt: RandomInt = (from, until) => from + Math.floor(Math.random() * (until - from));

const clamp = (n: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, n));

/** Arrotondamento di Kotlin roundToInt (meta' verso +infinito). */
const roundToInt = (x: number) => Math.floor(x + 0.5);

export function clampTarget(targetIntervalDays: number): number {
  return Math.max(MIN_INTERVAL_DAYS, Math.trunc(targetIntervalDays));
}

export function clampJitter(jitterPercent: number): number {
  return clamp(Math.trunc(jitterPercent), 0, MAX_JITTER_PERCENT);
}

/** Finestra inclusiva [low, high] di giorni per obiettivo e jitter (window). */
export function window(targetIntervalDays: number, jitterPercent: number): { low: number; high: number } {
  const target = clampTarget(targetIntervalDays);
  const jitter = clampJitter(jitterPercent);
  const delta = roundToInt((target * jitter) / 100.0);
  const low = Math.max(MIN_INTERVAL_DAYS, target - delta);
  const high = Math.max(low, target + delta);
  return { low, high };
}

/** Intervallo in giorni interi estratto uniformemente dalla finestra (nextIntervalDays). */
export function nextIntervalDays(targetIntervalDays: number, jitterPercent: number, random: RandomInt): number {
  const { low, high } = window(targetIntervalDays, jitterPercent);
  return random(low, high + 1);
}

/** Prossimo istante: `fromMs` piu' un intervallo appena estratto (nextFireAt). */
export function nextFireAt(fromMs: number, targetIntervalDays: number, jitterPercent: number, random: RandomInt): number {
  return fromMs + nextIntervalDays(targetIntervalDays, jitterPercent, random) * DAY_MS;
}

export const REMINDER_CATEGORIES = ['LOVE', 'FRIENDSHIP', 'FAMILY', 'CUSTOM'] as const;
export type ReminderCategory = (typeof REMINDER_CATEGORIES)[number];

/** Categoria salvata -> enum, CUSTOM per valori sconosciuti (come toReminderCategory nei mapper). */
export function toReminderCategory(value: unknown): ReminderCategory {
  return (REMINDER_CATEGORIES as readonly string[]).includes(String(value)) ? (value as ReminderCategory) : 'CUSTOM';
}

export interface ReminderPreset {
  key: string;
  category: ReminderCategory;
  targetIntervalDays: number;
  jitterPercent: number;
}

/** Preset dell'app (ReminderPresets.all): titolo e descrizione localizzati stanno nell'app, qui solo le manopole. */
export const REMINDER_PRESETS: readonly ReminderPreset[] = [
  { key: 'love_new_activity', category: 'LOVE', targetIntervalDays: 30, jitterPercent: 30 },
  { key: 'love_state_of_union', category: 'LOVE', targetIntervalDays: 7, jitterPercent: DEFAULT_JITTER_PERCENT },
  { key: 'love_love_maps', category: 'LOVE', targetIntervalDays: 7, jitterPercent: DEFAULT_JITTER_PERCENT },
  { key: 'friend_face_time', category: 'FRIENDSHIP', targetIntervalDays: 14, jitterPercent: DEFAULT_JITTER_PERCENT },
  { key: 'friend_ask_advice', category: 'FRIENDSHIP', targetIntervalDays: 30, jitterPercent: 30 },
  { key: 'family_parents_call', category: 'FAMILY', targetIntervalDays: 7, jitterPercent: DEFAULT_JITTER_PERCENT },
  { key: 'family_siblings', category: 'FAMILY', targetIntervalDays: 21, jitterPercent: DEFAULT_JITTER_PERCENT },
  { key: 'family_grandparents', category: 'FAMILY', targetIntervalDays: 30, jitterPercent: DEFAULT_JITTER_PERCENT },
];

export function presetByKey(key: string): ReminderPreset | undefined {
  return REMINDER_PRESETS.find((p) => p.key === key);
}
