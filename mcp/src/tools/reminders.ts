/**
 * Promemoria per persona con cadenza irregolare (ReminderUseCases.kt): creazione con il primo nextFireAt estratto,
 * modifica che ricalcola la data solo se cambia la cadenza, "fatto" che registra il completamento e riestrae la data.
 * Lo storico dei "fatto" (reminderCompletions) si legge, si completa con date passate, si corregge e si cancella
 * (tombstone); dopo ogni modifica la pianificazione si ricalcola con scheduleAfterHistoryChange.
 */
import { z } from 'zod';
import { formatLocalDate, fromEpochDay, localDateOf, requireLocalDate, toEpochDay, zonedEpoch } from '../domain/dates.js';
import { ToolError } from '../domain/errors.js';
import {
  clampJitter,
  clampTarget,
  completionDay,
  completionInstant,
  DEFAULT_JITTER_PERCENT,
  latestCompletion,
  MAX_JITTER_PERCENT,
  nextFireAt,
  presetByKey,
  REMINDER_CATEGORIES,
  REMINDER_PRESETS,
  scheduleAfterHistoryChange,
  window,
} from '../domain/reminders.js';
import type { Doc, SyncBatch } from '../store.js';
import { clock, cleanText, dayString, defineTool, docIdSchema, hourMinute, instantString, page, pagination, parseInstant, timeOfDay, timeString, type Clock, type ToolContext } from './helpers.js';
import { byId, completionView, num, reminderView, requirePerson, str } from './views.js';

async function requireReminder(ctx: ToolContext, id: string) {
  const r = await ctx.store.get('reminders', id);
  if (!r) throw new ToolError(`Reminder not found: ${id} (use list_reminders to get ids)`);
  return r;
}

async function requireCompletion(ctx: ToolContext, id: string) {
  const c = await ctx.store.get('reminderCompletions', id);
  if (!c) throw new ToolError(`Completion not found: ${id} (use list_reminder_completions to get ids)`);
  return c;
}

const categorySchema = z.enum(REMINDER_CATEGORIES);
const NOON_HOUR = 12;

/** Mezzogiorno locale di un epoch day: l'istante dei completamenti che hanno solo il giorno. */
const noonOf = (clk: Clock) => (epochDay: number) => zonedEpoch(fromEpochDay(epochDay), NOON_HOUR, 0, clk.tz);

function presetKeyField(value: string | null): string | null {
  if (value === null) return null;
  if (!presetByKey(value)) throw new ToolError(`Unknown presetKey: ${value}. Known: ${REMINDER_PRESETS.map((p) => p.key).join(', ')}`);
  return value;
}

/** Un "fatto" non puo stare nel futuro: rovinerebbe la cadenza (per eccezioni c'e raw_write). */
function assertNotFuture(clk: Clock, day: number, at: number | null): void {
  if (day > toEpochDay(clk.today) || (at !== null && at > clk.nowMs)) throw new ToolError('A completion cannot be in the future');
}

/**
 * Ricalcola la pianificazione di `reminder` dopo che il suo storico e' passato da `before` ad `after` e aggiunge
 * l'aggiornamento al lotto. Restituisce cosa e' successo, per la risposta del tool.
 */
function applyHistoryChange(ctx: ToolContext, batch: SyncBatch, clk: Clock, reminder: Doc, before: Doc[], after: Doc[], reschedule: boolean) {
  const noon = noonOf(clk);
  const next = reschedule
    ? scheduleAfterHistoryChange({
        latestBefore: latestCompletion(before, noon),
        latestAfter: latestCompletion(after, noon),
        targetIntervalDays: num(reminder.targetIntervalDays) ?? 1,
        jitterPercent: num(reminder.jitterPercent) ?? DEFAULT_JITTER_PERCENT,
        nowMs: clk.nowMs,
        random: ctx.random,
      })
    : null;
  if (next) batch.update('reminders', reminder.id, next);
  const view = reminderView({ ...reminder, ...(next ?? {}) }, clk, new Map(), after);
  return { reminderId: reminder.id, rescheduled: next !== null, lastFiredAt: view.lastFiredAt, nextFireDate: view.nextFireDate, daysUntilNext: view.daysUntilNext, timesDone: view.timesDone, lastDoneOn: view.lastDoneOn };
}

/* ------------------------------------------------------------------ promemoria */

export const listReminders = defineTool({
  name: 'list_reminders',
  title: 'List reminders',
  description:
    'Relationship reminders, soonest due first, with next date, days until (negative = overdue), cadence, last fire, how many times they were done and the last day done. withinDays keeps only those due within N days from today (overdue included). Optional personId, category, includePaused (default false). Also returns the built-in presets. The full done history is in list_reminder_completions.',
  input: {
    withinDays: z.number().int().min(0).max(3650).optional(),
    personId: docIdSchema.optional(),
    category: categorySchema.optional(),
    includePaused: z.boolean().default(false),
    ...pagination,
  },
  readOnly: true,
  handler: async (args, ctx) => {
    const [reminders, people, completions, clk] = await Promise.all([ctx.store.all('reminders'), ctx.store.all('people'), ctx.store.all('reminderCompletions'), clock(ctx)]);
    const peopleById = byId(people);
    const rows = reminders
      .filter((r) => args.includePaused || r.enabled !== false)
      .filter((r) => !args.personId || str(r.personId) === args.personId)
      .map((r) => reminderView(r, clk, peopleById, completions))
      .filter((r) => !args.category || r.category === args.category)
      .filter((r) => args.withinDays === undefined || r.daysUntilNext <= args.withinDays)
      .sort((a, b) => a.nextFireAt.localeCompare(b.nextFireAt));
    return { today: clk.todayIso, timeZone: clk.tz, presets: REMINDER_PRESETS, ...page(rows, args.limit, args.offset) };
  },
});

export const createReminder = defineTool({
  name: 'create_reminder',
  title: 'Create a reminder',
  description: `Adds a recurring reminder for a person ("call mum about every week"). The next date is drawn at random in a window around targetIntervalDays sized by jitterPercent (default ${DEFAULT_JITTER_PERCENT}, max ${MAX_JITTER_PERCENT}; 0 = fixed), like the app. presetKey fills category, interval and jitter from a built-in preset (see list_reminders).`,
  input: {
    personId: docIdSchema,
    title: z.string().min(1).max(300),
    note: z.string().max(5000).optional(),
    category: categorySchema.optional(),
    targetIntervalDays: z.number().int().min(1).max(3650).optional(),
    jitterPercent: z.number().int().min(0).max(MAX_JITTER_PERCENT).optional(),
    presetKey: z.string().max(100).optional(),
    enabled: z.boolean().default(true),
  },
  handler: async (args, ctx) => {
    await requirePerson(ctx, args.personId);
    const title = args.title.trim();
    if (!title) throw new ToolError('title is required');
    const preset = args.presetKey ? presetByKey(args.presetKey) : undefined;
    if (args.presetKey && !preset) throw new ToolError(`Unknown presetKey: ${args.presetKey}. Known: ${REMINDER_PRESETS.map((p) => p.key).join(', ')}`);
    const rawTarget = args.targetIntervalDays ?? preset?.targetIntervalDays;
    if (rawTarget === undefined) throw new ToolError('targetIntervalDays is required (or a presetKey)');
    const target = clampTarget(rawTarget);
    const jitter = clampJitter(args.jitterPercent ?? preset?.jitterPercent ?? DEFAULT_JITTER_PERCENT);
    const now = ctx.now().getTime();
    const id = ctx.newId();
    const next = nextFireAt(now, target, jitter, ctx.random);
    await ctx.store
      .batch()
      .create('reminders', id, {
        personId: args.personId,
        title,
        note: cleanText(args.note),
        category: args.category ?? preset?.category ?? 'CUSTOM',
        targetIntervalDays: target,
        jitterPercent: jitter,
        enabled: args.enabled,
        lastFiredAt: null,
        nextFireAt: next,
        createdAt: now,
        presetKey: preset?.key ?? null,
      })
      .commit();
    const clk = await clock(ctx);
    const w = window(target, jitter);
    return { created: true, id, nextFireDate: formatLocalDate(localDateOf(next, clk.tz)), window: { minDays: w.low, maxDays: w.high } };
  },
});

export const updateReminder = defineTool({
  name: 'update_reminder',
  title: 'Update a reminder',
  description:
    'Changes only the given fields; every field of the reminder can be set. Like the app, the next date is redrawn only when the cadence (targetIntervalDays or jitterPercent) changes, from the last fire (or now if it never fired). Overrides: nextFireAt (ISO instant) or nextFireDate (+ optional nextFireTime, default the current time of day) set the next date exactly; redraw draws a new one from the last fire or from now. personId moves it to another person; lastFiredAt (null clears), createdAt and presetKey (null clears) are set as given. enabled=false pauses it, note null clears it. The done history is edited with the *_reminder_completion tools.',
  input: {
    id: docIdSchema,
    title: z.string().min(1).max(300).optional(),
    note: z.string().max(5000).nullable().optional(),
    category: categorySchema.optional(),
    targetIntervalDays: z.number().int().min(1).max(3650).optional(),
    jitterPercent: z.number().int().min(0).max(MAX_JITTER_PERCENT).optional(),
    enabled: z.boolean().optional(),
    personId: docIdSchema.optional().describe('Move the reminder to another person.'),
    presetKey: z.string().max(100).nullable().optional().describe('Built-in preset it comes from (null = custom). Does not change the cadence by itself.'),
    lastFiredAt: instantString.nullable().optional().describe('When it last notified (null = never).'),
    createdAt: instantString.optional(),
    nextFireAt: instantString.optional().describe('Exact next due instant.'),
    nextFireDate: dayString.optional().describe('Next due day (YYYY-MM-DD, user time zone).'),
    nextFireTime: timeString.optional().describe('Time of day for nextFireDate (default: keep the current one).'),
    redraw: z.enum(['fromLastFired', 'fromNow']).optional().describe('Draw a new next date with the cadence, from the last fire (or now if never fired) or from now.'),
  },
  idempotent: true,
  handler: async (args, ctx) => {
    const r = await requireReminder(ctx, args.id);
    const clk = await clock(ctx);
    const patch: Record<string, unknown> = {};
    if (args.title !== undefined) {
      if (!args.title.trim()) throw new ToolError('title cannot be empty');
      patch.title = args.title.trim();
    }
    if (args.note !== undefined) patch.note = cleanText(args.note);
    if (args.category !== undefined) patch.category = args.category;
    if (args.enabled !== undefined) patch.enabled = args.enabled;
    if (args.personId !== undefined && args.personId !== str(r.personId)) {
      await requirePerson(ctx, args.personId);
      patch.personId = args.personId;
    }
    if (args.presetKey !== undefined) patch.presetKey = presetKeyField(args.presetKey);
    if (args.createdAt !== undefined) patch.createdAt = parseInstant(args.createdAt, 'createdAt');
    if (args.lastFiredAt !== undefined) patch.lastFiredAt = args.lastFiredAt === null ? null : parseInstant(args.lastFiredAt, 'lastFiredAt');

    const explicitNext = args.nextFireAt !== undefined || args.nextFireDate !== undefined || args.nextFireTime !== undefined;
    if (args.nextFireAt !== undefined && (args.nextFireDate !== undefined || args.nextFireTime !== undefined)) throw new ToolError('Pass either nextFireAt or nextFireDate/nextFireTime, not both');
    if (args.nextFireTime !== undefined && args.nextFireDate === undefined) throw new ToolError('nextFireTime goes with nextFireDate');
    if (explicitNext && args.redraw) throw new ToolError('Pass either an explicit next date or redraw, not both');

    const oldTarget = num(r.targetIntervalDays) ?? 1;
    const oldJitter = num(r.jitterPercent) ?? DEFAULT_JITTER_PERCENT;
    const target = clampTarget(args.targetIntervalDays ?? oldTarget);
    const jitter = clampJitter(args.jitterPercent ?? oldJitter);
    const cadenceChanged = target !== oldTarget || jitter !== oldJitter;
    if (cadenceChanged) {
      patch.targetIntervalDays = target;
      patch.jitterPercent = jitter;
    }
    const lastFired = 'lastFiredAt' in patch ? (patch.lastFiredAt as number | null) : num(r.lastFiredAt);
    if (args.nextFireAt !== undefined) patch.nextFireAt = parseInstant(args.nextFireAt, 'nextFireAt');
    else if (args.nextFireDate !== undefined) {
      const current = num(r.nextFireAt) ?? clk.nowMs;
      const [h, m] = hourMinute(args.nextFireTime ?? timeOfDay(current, clk.tz));
      patch.nextFireAt = zonedEpoch(requireLocalDate(args.nextFireDate, 'nextFireDate'), h, m, clk.tz);
    } else if (args.redraw) {
      const base = args.redraw === 'fromNow' ? clk.nowMs : (lastFired ?? clk.nowMs);
      patch.nextFireAt = nextFireAt(base, target, jitter, ctx.random);
    } else if (cadenceChanged) {
      patch.nextFireAt = nextFireAt(lastFired ?? clk.nowMs, target, jitter, ctx.random);
    }
    if (Object.keys(patch).length === 0) throw new ToolError('Nothing to update: pass at least one field');
    await ctx.store.batch().update('reminders', args.id, patch).commit();
    const view = reminderView({ ...r, ...patch }, clk, new Map(), []);
    return { updated: true, id: args.id, fields: Object.keys(patch), rescheduled: 'nextFireAt' in patch, nextFireDate: view.nextFireDate, nextFireAt: view.nextFireAt, daysUntilNext: view.daysUntilNext };
  },
});

export const deleteReminder = defineTool({
  name: 'delete_reminder',
  title: 'Delete a reminder',
  description: 'Marks a reminder and its completion history as deleted (tombstones). Only on explicit request; to stop it temporarily use update_reminder with enabled=false.',
  input: { id: docIdSchema },
  destructive: true,
  handler: async ({ id }, ctx) => {
    const r = await requireReminder(ctx, id);
    const completions = (await ctx.store.all('reminderCompletions')).filter((c) => str(c.reminderId) === id);
    const batch = ctx.store.batch().tombstone('reminders', id);
    for (const c of completions) batch.tombstone('reminderCompletions', c.id);
    await batch.commit();
    return { deleted: true, id, title: str(r.title), completionsDeleted: completions.length };
  },
});

export const markReminderDone = defineTool({
  name: 'mark_reminder_done',
  title: 'Mark a reminder done',
  description: 'Ticks a reminder off as done now, like the "done" button: appends a completion (day in the user time zone + instant, optional note) and restarts the cadence from now with a freshly drawn interval. For a past date use add_reminder_completion.',
  input: { id: docIdSchema, note: z.string().max(5000).optional().describe('Optional note on this completion.') },
  handler: async ({ id, note }, ctx) => {
    const r = await requireReminder(ctx, id);
    const clk = await clock(ctx);
    const now = clk.nowMs;
    const next = nextFireAt(now, num(r.targetIntervalDays) ?? 1, num(r.jitterPercent) ?? DEFAULT_JITTER_PERCENT, ctx.random);
    const completionId = ctx.newId();
    const clean = cleanText(note);
    await ctx.store
      .batch()
      .create('reminderCompletions', completionId, { reminderId: id, day: toEpochDay(clk.today), at: now, ...(clean ? { note: clean } : {}) })
      .update('reminders', id, { lastFiredAt: now, nextFireAt: next })
      .commit();
    const view = reminderView({ ...r, lastFiredAt: now, nextFireAt: next }, clk, new Map(), []);
    return { done: true, id, completionId, doneOn: clk.todayIso, nextFireDate: view.nextFireDate, daysUntilNext: view.daysUntilNext };
  },
});

/* ------------------------------------------------------------------ storico dei "fatto" */

const rescheduleSchema = z
  .boolean()
  .default(true)
  .describe('Recompute lastFiredAt/nextFireAt from the history like the app (default true); false = history only, schedule untouched.');

export const listReminderCompletions = defineTool({
  name: 'list_reminder_completions',
  title: 'List reminder completions (done history)',
  description:
    'The full "done" history of reminders, most recent first: one row per tap with completion id, reminder, person, local date, exact instant (null when only the day is known) and note. Filter by reminderId or personId (all reminders of that person) and by period (from/to inclusive, YYYY-MM-DD in the user time zone). Without filters: every completion of every reminder.',
  input: {
    reminderId: docIdSchema.optional(),
    personId: docIdSchema.optional(),
    from: dayString.optional(),
    to: dayString.optional(),
    ...pagination,
  },
  readOnly: true,
  handler: async (args, ctx) => {
    const [completions, reminders, people, clk] = await Promise.all([ctx.store.all('reminderCompletions'), ctx.store.all('reminders'), ctx.store.all('people'), clock(ctx)]);
    const from = args.from ? toEpochDay(requireLocalDate(args.from, 'from')) : -Infinity;
    const to = args.to ? toEpochDay(requireLocalDate(args.to, 'to')) : Infinity;
    if (from > to) throw new ToolError('from is after to');
    const remindersById = byId(reminders);
    const noon = noonOf(clk);
    const rows = completions
      .filter((c) => !args.reminderId || str(c.reminderId) === args.reminderId)
      .filter((c) => !args.personId || str(remindersById.get(str(c.reminderId))?.personId) === args.personId)
      .map((c) => ({ c, day: completionDay(c), t: completionInstant(c, noon) ?? 0 }))
      .filter(({ day }) => day !== null && day >= from && day <= to)
      .sort((a, b) => b.t - a.t)
      .map(({ c }) => completionView(c, clk, remindersById, byId(people)));
    return { today: clk.todayIso, timeZone: clk.tz, ...page(rows, args.limit, args.offset) };
  },
});

export const addReminderCompletion = defineTool({
  name: 'add_reminder_completion',
  title: 'Add a past completion',
  description:
    'Records that a reminder was done at a given moment, also in the past: at (ISO instant), or date (YYYY-MM-DD) with optional time (HH:mm; without it only the day is stored and counts as local noon), or nothing = now. Optional note. Not in the future. Then the schedule is recomputed exactly like the app: only if the latest completion changed, nextFireAt = latest completion + a freshly drawn interval (it can land in the past = due); lastFiredAt is not touched.',
  input: {
    reminderId: docIdSchema,
    at: instantString.optional(),
    date: dayString.optional(),
    time: timeString.optional(),
    note: z.string().max(5000).optional(),
    reschedule: rescheduleSchema,
  },
  handler: async (args, ctx) => {
    const [r, completions, clk] = await Promise.all([requireReminder(ctx, args.reminderId), ctx.store.all('reminderCompletions'), clock(ctx)]);
    if (args.at !== undefined && (args.date !== undefined || args.time !== undefined)) throw new ToolError('Pass either at or date/time, not both');
    let day: number;
    let at: number | null;
    if (args.at !== undefined) {
      at = parseInstant(args.at);
      day = toEpochDay(localDateOf(at, clk.tz));
    } else if (args.date !== undefined || args.time !== undefined) {
      const d = args.date ? requireLocalDate(args.date, 'date') : clk.today;
      day = toEpochDay(d);
      at = args.time ? zonedEpoch(d, ...hourMinute(args.time), clk.tz) : null;
    } else {
      at = clk.nowMs;
      day = toEpochDay(clk.today);
    }
    assertNotFuture(clk, day, at);
    const id = ctx.newId();
    const note = cleanText(args.note);
    const data = { reminderId: args.reminderId, day, at, ...(note ? { note } : {}) };
    const before = completions.filter((c) => str(c.reminderId) === args.reminderId);
    const batch = ctx.store.batch().create('reminderCompletions', id, data);
    const schedule = applyHistoryChange(ctx, batch, clk, r, before, [...before, { id, ...data }], args.reschedule);
    await batch.commit();
    return { created: true, completionId: id, date: formatLocalDate(fromEpochDay(day)), schedule };
  },
});

export const updateReminderCompletion = defineTool({
  name: 'update_reminder_completion',
  title: 'Edit a completion',
  description:
    'Edits one completion of the done history: at (ISO instant), or date and/or time (date alone keeps the time of day; time null keeps only the day), note (null clears), reminderId (moves it to another reminder). Not in the future. The schedule of every reminder touched is recomputed like the app (see add_reminder_completion) unless reschedule is false.',
  input: {
    id: docIdSchema,
    at: instantString.optional(),
    date: dayString.optional(),
    time: timeString.nullable().optional(),
    note: z.string().max(5000).nullable().optional(),
    reminderId: docIdSchema.optional(),
    reschedule: rescheduleSchema,
  },
  idempotent: true,
  handler: async (args, ctx) => {
    const [c, completions, clk] = await Promise.all([requireCompletion(ctx, args.id), ctx.store.all('reminderCompletions'), clock(ctx)]);
    if (args.at !== undefined && (args.date !== undefined || args.time !== undefined)) throw new ToolError('Pass either at or date/time, not both');
    const patch: Record<string, unknown> = {};
    const curDay = completionDay(c);
    const curAt = num(c.at);
    if (args.at !== undefined) {
      patch.at = parseInstant(args.at);
      patch.day = toEpochDay(localDateOf(patch.at as number, clk.tz));
    } else if (args.date !== undefined || args.time !== undefined) {
      const d = args.date ? requireLocalDate(args.date, 'date') : curDay !== null ? fromEpochDay(curDay) : clk.today;
      const time = args.time === undefined ? (curAt === null ? null : timeOfDay(curAt, clk.tz)) : args.time;
      patch.day = toEpochDay(d);
      patch.at = time === null ? null : zonedEpoch(d, ...hourMinute(time), clk.tz);
    }
    if ('day' in patch) assertNotFuture(clk, patch.day as number, patch.at as number | null);
    if (args.note !== undefined) patch.note = cleanText(args.note);
    const oldReminderId = str(c.reminderId);
    if (args.reminderId !== undefined && args.reminderId !== oldReminderId) patch.reminderId = args.reminderId;
    if (Object.keys(patch).length === 0) throw new ToolError('Nothing to update: pass at least one field');

    const newReminderId = (patch.reminderId as string | undefined) ?? oldReminderId;
    const target = newReminderId !== oldReminderId ? await requireReminder(ctx, newReminderId) : null;
    const source = await ctx.store.get('reminders', oldReminderId);
    const updated: Doc = { ...c, ...patch };
    const after = completions.map((x) => (x.id === c.id ? updated : x));
    const batch = ctx.store.batch().update('reminderCompletions', c.id, patch);
    const schedules = [];
    for (const reminder of [source, target]) {
      if (!reminder) continue;
      const mine = (list: Doc[]) => list.filter((x) => str(x.reminderId) === reminder.id);
      schedules.push(applyHistoryChange(ctx, batch, clk, reminder, mine(completions), mine(after), args.reschedule));
    }
    await batch.commit();
    return { updated: true, completionId: c.id, fields: Object.keys(patch), completion: completionView(updated, clk, new Map(), new Map()), schedules };
  },
});

export const deleteReminderCompletion = defineTool({
  name: 'delete_reminder_completion',
  title: 'Delete a completion',
  description: 'Removes one completion from the done history (tombstone, as the app expects). If it was the latest, nextFireAt is redrawn from the latest remaining completion; when none is left, lastFiredAt is cleared and nextFireAt is drawn from now, like the app. Only on explicit request.',
  input: { id: docIdSchema, reschedule: rescheduleSchema },
  destructive: true,
  handler: async (args, ctx) => {
    const [c, completions, clk] = await Promise.all([requireCompletion(ctx, args.id), ctx.store.all('reminderCompletions'), clock(ctx)]);
    const reminder = await ctx.store.get('reminders', str(c.reminderId));
    const batch = ctx.store.batch().tombstone('reminderCompletions', c.id);
    let schedule = null;
    if (reminder) {
      const before = completions.filter((x) => str(x.reminderId) === reminder.id);
      schedule = applyHistoryChange(ctx, batch, clk, reminder, before, before.filter((x) => x.id !== c.id), args.reschedule);
    }
    await batch.commit();
    return { deleted: true, completionId: c.id, schedule };
  },
});
