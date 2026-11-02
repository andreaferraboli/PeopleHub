/**
 * Promemoria per persona con cadenza irregolare (ReminderUseCases.kt): creazione con il primo nextFireAt estratto,
 * modifica che ricalcola la data solo se cambia la cadenza, "fatto" che registra il completamento e riestrae la data.
 */
import { z } from 'zod';
import { formatLocalDate, localDateOf, toEpochDay } from '../domain/dates.js';
import { ToolError } from '../domain/errors.js';
import { clampJitter, clampTarget, DEFAULT_JITTER_PERCENT, MAX_JITTER_PERCENT, nextFireAt, presetByKey, REMINDER_CATEGORIES, REMINDER_PRESETS, window } from '../domain/reminders.js';
import { clock, cleanText, defineTool, docIdSchema, page, pagination, type ToolContext } from './helpers.js';
import { byId, num, reminderView, requirePerson, str } from './views.js';

async function requireReminder(ctx: ToolContext, id: string) {
  const r = await ctx.store.get('reminders', id);
  if (!r) throw new ToolError(`Reminder not found: ${id} (use list_reminders to get ids)`);
  return r;
}

const categorySchema = z.enum(REMINDER_CATEGORIES);

export const listReminders = defineTool({
  name: 'list_reminders',
  title: 'List reminders',
  description:
    'Relationship reminders, soonest due first, with next date, days until (negative = overdue), cadence and how many times they were done. withinDays keeps only those due within N days from today (overdue included). Optional personId, category, includePaused (default false). Also returns the built-in presets.',
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
  description: 'Changes only the given fields. The next date is redrawn only when the cadence (targetIntervalDays or jitterPercent) changes, from the last fire (or now if it never fired), like the app. enabled=false pauses it. note null clears it.',
  input: {
    id: docIdSchema,
    title: z.string().min(1).max(300).optional(),
    note: z.string().max(5000).nullable().optional(),
    category: categorySchema.optional(),
    targetIntervalDays: z.number().int().min(1).max(3650).optional(),
    jitterPercent: z.number().int().min(0).max(MAX_JITTER_PERCENT).optional(),
    enabled: z.boolean().optional(),
  },
  idempotent: true,
  handler: async (args, ctx) => {
    const r = await requireReminder(ctx, args.id);
    const patch: Record<string, unknown> = {};
    if (args.title !== undefined) {
      if (!args.title.trim()) throw new ToolError('title cannot be empty');
      patch.title = args.title.trim();
    }
    if (args.note !== undefined) patch.note = cleanText(args.note);
    if (args.category !== undefined) patch.category = args.category;
    if (args.enabled !== undefined) patch.enabled = args.enabled;
    const oldTarget = num(r.targetIntervalDays) ?? 1;
    const oldJitter = num(r.jitterPercent) ?? DEFAULT_JITTER_PERCENT;
    const target = clampTarget(args.targetIntervalDays ?? oldTarget);
    const jitter = clampJitter(args.jitterPercent ?? oldJitter);
    if (target !== oldTarget || jitter !== oldJitter) {
      patch.targetIntervalDays = target;
      patch.jitterPercent = jitter;
      patch.nextFireAt = nextFireAt(num(r.lastFiredAt) ?? ctx.now().getTime(), target, jitter, ctx.random);
    }
    if (Object.keys(patch).length === 0) throw new ToolError('Nothing to update: pass at least one field');
    await ctx.store.batch().update('reminders', args.id, patch).commit();
    return { updated: true, id: args.id, fields: Object.keys(patch), rescheduled: 'nextFireAt' in patch };
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
  description: 'Ticks a reminder off as done now, like the "done" button: appends a completion (day in the user time zone + instant) and restarts the cadence from now with a freshly drawn interval.',
  input: { id: docIdSchema },
  handler: async ({ id }, ctx) => {
    const r = await requireReminder(ctx, id);
    const clk = await clock(ctx);
    const now = clk.nowMs;
    const next = nextFireAt(now, num(r.targetIntervalDays) ?? 1, num(r.jitterPercent) ?? DEFAULT_JITTER_PERCENT, ctx.random);
    const completionId = ctx.newId();
    await ctx.store
      .batch()
      .create('reminderCompletions', completionId, { reminderId: id, day: toEpochDay(clk.today), at: now })
      .update('reminders', id, { lastFiredAt: now, nextFireAt: next })
      .commit();
    const view = reminderView({ ...r, lastFiredAt: now, nextFireAt: next }, clk, new Map(), []);
    return { done: true, id, completionId, doneOn: clk.todayIso, nextFireDate: view.nextFireDate, daysUntilNext: view.daysUntilNext };
  },
});
