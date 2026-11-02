/**
 * Check-in e uscite. Come nell'app un'uscita e' la vista sui check-in che condividono `outingId`
 * (uno per persona presente): check_in ne crea una nuova, update_outing e delete_outing la trattano come un'unita
 * (UpdateOutingUseCase, DeleteOutingUseCase) e ricalcolano lastCheckInAt di chi e' coinvolto.
 */
import { z } from 'zod';
import { formatLocalDate, localDateOf, requireLocalDate, toEpochDay, zonedEpoch } from '../domain/dates.js';
import { ToolError } from '../domain/errors.js';
import type { Doc } from '../store.js';
import { clock, cleanText, dayString, defineTool, docIdSchema, page, pagination, timeString, type Clock, type ToolContext } from './helpers.js';
import { byId, fullName, groupOutings, num, refreshLastCheckIn, str } from './views.js';

/** Ora di riferimento dei giorni registrati (RecordMeetupUseCase: mezzogiorno nel fuso). */
const NOON = '12:00';

function instantFor(clk: Clock, date: string | undefined, time: string | undefined): number {
  if (!date && !time) return clk.nowMs;
  const day = date ? requireLocalDate(date, 'date') : clk.today;
  const [h, m] = (time ?? NOON).split(':').map(Number);
  return zonedEpoch(day, h, m, clk.tz);
}

async function requirePeople(ctx: ToolContext, ids: string[]): Promise<Doc[]> {
  const people = await ctx.store.all('people');
  const known = new Set(people.map((p) => p.id));
  const missing = ids.filter((id) => !known.has(id));
  if (missing.length) throw new ToolError(`Person not found: ${missing.join(', ')} (use list_people to get ids)`);
  return people;
}

async function outingRows(ctx: ToolContext, outingId: string): Promise<{ all: Doc[]; rows: Doc[] }> {
  const all = await ctx.store.all('checkIns');
  const rows = all.filter((c) => str(c.outingId) === outingId);
  if (rows.length === 0) throw new ToolError(`Outing not found: ${outingId} (use list_outings to get ids)`);
  return { all, rows };
}

export const checkIn = defineTool({
  name: 'check_in',
  title: 'Record a check-in / outing',
  description:
    'Records that you saw one or more people: one check-in per person, all sharing a new outingId (one outing, like "record an outing" in the app). Without date the check-in is now; with date (YYYY-MM-DD, in the user time zone) it is that day at noon, or at time (HH:mm) if given. Updates each person\'s last-seen date. Use list_people first to get ids.',
  input: {
    personIds: z.array(docIdSchema).min(1).max(50).describe('People who were there.'),
    date: dayString.optional().describe('Day of the meeting (default: now).'),
    time: timeString.optional().describe('Time of day (default: noon when date is given).'),
    note: z.string().max(5000).optional().describe('Shared description of the outing.'),
  },
  handler: async (args, ctx) => {
    const ids = [...new Set(args.personIds)];
    const [people, existing, clk] = await Promise.all([requirePeople(ctx, ids), ctx.store.all('checkIns'), clock(ctx)]);
    const at = instantFor(clk, args.date, args.time);
    const outingId = ctx.newId();
    const note = cleanText(args.note);
    const batch = ctx.store.batch();
    const created: Doc[] = [];
    for (const personId of ids) {
      const id = ctx.newId();
      const data = { personId, at, note, outingId };
      batch.create('checkIns', id, data);
      created.push({ id, ...data });
    }
    refreshLastCheckIn(batch, people, [...existing, ...created], ids);
    await batch.commit();
    const names = byId(people);
    return {
      created: true,
      outingId,
      date: formatLocalDate(localDateOf(at, clk.tz)),
      checkIns: created.map((c) => ({ id: c.id, personId: str(c.personId), name: fullName(names.get(str(c.personId))!) })),
    };
  },
});

export const listOutings = defineTool({
  name: 'list_outings',
  title: 'List outings',
  description: 'Outings history, most recent first: check-ins grouped by outingId with date, shared note and attendees. Optional period (from/to inclusive, YYYY-MM-DD in the user time zone) and personId (outings that person attended).',
  input: {
    from: dayString.optional(),
    to: dayString.optional(),
    personId: docIdSchema.optional(),
    ...pagination,
  },
  readOnly: true,
  handler: async (args, ctx) => {
    const [checkIns, people, clk] = await Promise.all([ctx.store.all('checkIns'), ctx.store.all('people'), clock(ctx)]);
    const from = args.from ? toEpochDay(requireLocalDate(args.from, 'from')) : -Infinity;
    const to = args.to ? toEpochDay(requireLocalDate(args.to, 'to')) : Infinity;
    if (from > to) throw new ToolError('from is after to');
    let outings = groupOutings(checkIns, byId(people), clk.tz).filter((o) => {
      const d = toEpochDay(localDateOf(o.atMs, clk.tz));
      return d >= from && d <= to;
    });
    if (args.personId) outings = outings.filter((o) => o.attendees.some((a) => a.personId === args.personId));
    const items = outings.map(({ atMs: _atMs, ...rest }) => rest);
    return { timeZone: clk.tz, ...page(items, args.limit, args.offset) };
  },
});

export const updateOuting = defineTool({
  name: 'update_outing',
  title: 'Edit an outing',
  description:
    'Rewrites an outing as a whole, like the app: new day (noon, or time), new shared note (null clears it) and/or the full attendee list (people removed lose their check-in, people added get one). Everyone touched has their last-seen date recomputed. Fields not given stay as they are.',
  input: {
    outingId: docIdSchema,
    date: dayString.optional(),
    time: timeString.optional(),
    note: z.string().max(5000).nullable().optional(),
    personIds: z.array(docIdSchema).min(1).max(50).optional().describe('Full list of attendees (replaces the current one).'),
  },
  destructive: true,
  handler: async (args, ctx) => {
    const [{ all, rows }, clk] = await Promise.all([outingRows(ctx, args.outingId), clock(ctx)]);
    const currentAt = Math.max(...rows.map((r) => num(r.at) ?? 0));
    let at = currentAt;
    if (args.date || args.time) {
      const day = args.date ? requireLocalDate(args.date, 'date') : localDateOf(currentAt, clk.tz);
      const [h, m] = (args.time ?? NOON).split(':').map(Number);
      at = zonedEpoch(day, h, m, clk.tz);
    }
    const note = args.note === undefined ? (rows.map((r) => cleanText(str(r.note))).find((n) => n !== null) ?? null) : cleanText(args.note);
    const before = rows.map((r) => str(r.personId));
    const wanted = args.personIds ? [...new Set(args.personIds)] : [...new Set(before)];
    const people = await requirePeople(ctx, wanted.filter((id) => !before.includes(id)));

    const batch = ctx.store.batch();
    const keptRowIds = new Set<string>();
    const after: Doc[] = all.filter((c) => str(c.outingId) !== args.outingId);
    for (const personId of wanted) {
      const row = rows.find((r) => str(r.personId) === personId && !keptRowIds.has(r.id));
      if (row) {
        keptRowIds.add(row.id);
        batch.update('checkIns', row.id, { personId, at, note, outingId: args.outingId });
        after.push({ ...row, at, note });
      } else {
        const id = ctx.newId();
        const data = { personId, at, note, outingId: args.outingId };
        batch.create('checkIns', id, data);
        after.push({ id, ...data });
      }
    }
    const removed = rows.filter((r) => !keptRowIds.has(r.id));
    for (const r of removed) batch.tombstone('checkIns', r.id);
    refreshLastCheckIn(batch, people, after, [...before, ...wanted]);
    await batch.commit();
    return { updated: true, outingId: args.outingId, date: formatLocalDate(localDateOf(at, clk.tz)), note, attendees: wanted.length, removedCheckIns: removed.length };
  },
});

export const deleteOuting = defineTool({
  name: 'delete_outing',
  title: 'Delete an outing',
  description: 'Deletes a whole outing: every attendee\'s check-in is marked deleted (tombstone) and their last-seen date is recomputed from the remaining history. Only on explicit request.',
  input: { outingId: docIdSchema },
  destructive: true,
  handler: async ({ outingId }, ctx) => {
    const [{ all, rows }, people] = await Promise.all([outingRows(ctx, outingId), ctx.store.all('people')]);
    const batch = ctx.store.batch();
    for (const r of rows) batch.tombstone('checkIns', r.id);
    refreshLastCheckIn(batch, people, all.filter((c) => str(c.outingId) !== outingId), rows.map((r) => str(r.personId)));
    await batch.commit();
    return { deleted: true, outingId, checkInsDeleted: rows.length };
  },
});
