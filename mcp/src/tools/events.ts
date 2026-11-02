/**
 * Eventi personali (contatore dei giorni passati o mancanti), opzionalmente legati a una persona.
 * Immagine di sfondo e "fissato nel widget" restano sul telefono (non sincronizzati).
 */
import { z } from 'zod';
import { localDateOf, requireLocalDate, toEpochDay, zonedEpoch } from '../domain/dates.js';
import { ToolError } from '../domain/errors.js';
import { clock, cleanText, dayString, defineTool, docIdSchema, page, pagination, timeString, type ToolContext } from './helpers.js';
import { byId, eventView, num, requirePerson, str } from './views.js';

const DEFAULT_TIME = '12:00';

async function requireEvent(ctx: ToolContext, id: string) {
  const e = await ctx.store.get('events', id);
  if (!e) throw new ToolError(`Event not found: ${id} (use list_events to get ids)`);
  return e;
}

export const listEvents = defineTool({
  name: 'list_events',
  title: 'List events',
  description: 'Personal events with days from today (negative = past). when: all (default, by date), upcoming (today and later, soonest first) or past (most recent first). Optional category, personId and period (from/to inclusive, YYYY-MM-DD).',
  input: {
    when: z.enum(['all', 'upcoming', 'past']).default('all'),
    category: z.string().max(100).optional(),
    personId: docIdSchema.optional(),
    from: dayString.optional(),
    to: dayString.optional(),
    ...pagination,
  },
  readOnly: true,
  handler: async (args, ctx) => {
    const [events, people, clk] = await Promise.all([ctx.store.all('events'), ctx.store.all('people'), clock(ctx)]);
    const from = args.from ? toEpochDay(requireLocalDate(args.from, 'from')) : -Infinity;
    const to = args.to ? toEpochDay(requireLocalDate(args.to, 'to')) : Infinity;
    const today = toEpochDay(clk.today);
    const rows = events
      .filter((e) => !args.category || str(e.category).toLowerCase() === args.category.toLowerCase())
      .filter((e) => !args.personId || str(e.personId) === args.personId)
      .map((e) => ({ e, day: toEpochDay(localDateOf(num(e.at) ?? 0, clk.tz)) }))
      .filter(({ day }) => day >= from && day <= to)
      .filter(({ day }) => (args.when === 'upcoming' ? day >= today : args.when === 'past' ? day < today : true))
      .sort((a, b) => (args.when === 'past' ? (num(b.e.at) ?? 0) - (num(a.e.at) ?? 0) : (num(a.e.at) ?? 0) - (num(b.e.at) ?? 0)))
      .map(({ e }) => eventView(e, clk, byId(people)));
    const categories = [...new Set(events.map((e) => str(e.category)).filter(Boolean))].sort();
    return { today: clk.todayIso, timeZone: clk.tz, categories, ...page(rows, args.limit, args.offset) };
  },
});

export const createEvent = defineTool({
  name: 'create_event',
  title: 'Create an event',
  description: 'Adds a personal event (anniversary, trip, milestone...) on date (YYYY-MM-DD) at time (HH:mm, default 12:00) in the user time zone, optionally linked to a person.',
  input: {
    title: z.string().min(1).max(300),
    date: dayString,
    time: timeString.optional(),
    description: z.string().max(5000).optional(),
    category: z.string().max(100).optional().describe('Free-form chip, e.g. "Anniversary".'),
    personId: docIdSchema.optional(),
  },
  handler: async (args, ctx) => {
    const title = args.title.trim();
    if (!title) throw new ToolError('title is required');
    if (args.personId) await requirePerson(ctx, args.personId);
    const clk = await clock(ctx);
    const [h, m] = (args.time ?? DEFAULT_TIME).split(':').map(Number);
    const at = zonedEpoch(requireLocalDate(args.date), h, m, clk.tz);
    const id = ctx.newId();
    await ctx.store
      .batch()
      .create('events', id, { title, at, description: cleanText(args.description), category: cleanText(args.category), personId: args.personId ?? null })
      .commit();
    return { created: true, id, title };
  },
});

export const updateEvent = defineTool({
  name: 'update_event',
  title: 'Update an event',
  description: 'Changes only the given fields of an event. date/time move it (the missing one is kept). description, category and personId accept null to clear.',
  input: {
    id: docIdSchema,
    title: z.string().min(1).max(300).optional(),
    date: dayString.optional(),
    time: timeString.optional(),
    description: z.string().max(5000).nullable().optional(),
    category: z.string().max(100).nullable().optional(),
    personId: docIdSchema.nullable().optional(),
  },
  idempotent: true,
  handler: async (args, ctx) => {
    const e = await requireEvent(ctx, args.id);
    const patch: Record<string, unknown> = {};
    if (args.title !== undefined) {
      if (!args.title.trim()) throw new ToolError('title cannot be empty');
      patch.title = args.title.trim();
    }
    if (args.date || args.time) {
      const clk = await clock(ctx);
      const current = num(e.at) ?? clk.nowMs;
      const day = args.date ? requireLocalDate(args.date) : localDateOf(current, clk.tz);
      const time = args.time ?? new Intl.DateTimeFormat('en-GB', { timeZone: clk.tz, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(current));
      const [h, m] = time.split(':').map(Number);
      patch.at = zonedEpoch(day, h, m, clk.tz);
    }
    if (args.description !== undefined) patch.description = cleanText(args.description);
    if (args.category !== undefined) patch.category = cleanText(args.category);
    if (args.personId !== undefined) {
      if (args.personId) await requirePerson(ctx, args.personId);
      patch.personId = args.personId;
    }
    if (Object.keys(patch).length === 0) throw new ToolError('Nothing to update: pass at least one field');
    await ctx.store.batch().update('events', args.id, patch).commit();
    return { updated: true, id: args.id, fields: Object.keys(patch) };
  },
});

export const deleteEvent = defineTool({
  name: 'delete_event',
  title: 'Delete an event',
  description: 'Marks an event as deleted (tombstone, synced to the phone). Only on explicit request.',
  input: { id: docIdSchema },
  destructive: true,
  handler: async ({ id }, ctx) => {
    const e = await requireEvent(ctx, id);
    await ctx.store.batch().tombstone('events', id).commit();
    return { deleted: true, id, title: str(e.title) };
  },
});
