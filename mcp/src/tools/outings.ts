/**
 * Check-in e uscite. Come nell'app un'uscita e' la vista sui check-in che condividono `outingId`
 * (uno per persona presente): check_in ne crea una nuova (una per giorno, RecordMeetupUseCase), update_outing e
 * delete_outing la trattano come un'unita (UpdateOutingUseCase, DeleteOutingUseCase), update_check_in e
 * delete_check_in toccano la riga di una sola persona (UpdateCheckInUseCase, DeleteCheckInsUseCase). Dopo ogni
 * modifica si ricalcola lastCheckInAt di chi e' coinvolto.
 */
import { z } from 'zod';
import { formatLocalDate, localDateOf, requireLocalDate, toEpochDay, zonedEpoch, type LocalDate } from '../domain/dates.js';
import { ToolError } from '../domain/errors.js';
import type { Doc } from '../store.js';
import { clock, cleanText, dayString, defineTool, docIdSchema, hourMinute, instantString, page, pagination, parseInstant, timeString, type Clock, type ToolContext } from './helpers.js';
import { byId, checkInView, fullName, groupOutings, num, refreshLastCheckIn, str } from './views.js';

/** Ora di riferimento dei giorni registrati (RecordMeetupUseCase: mezzogiorno nel fuso). */
const NOON = '12:00';

const atOn = (clk: Clock, day: LocalDate, time: string | undefined) => zonedEpoch(day, ...hourMinute(time ?? NOON), clk.tz);

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

const nameOf = (people: Map<string, Doc>, id: string) => {
  const p = people.get(id);
  return p ? fullName(p) : '(deleted person)';
};

export const checkIn = defineTool({
  name: 'check_in',
  title: 'Record a check-in / create an outing',
  description:
    'Creates an outing: records that you saw one or more people, one check-in per person, all sharing a new outingId (like "record an outing" in the app). When: nothing = now; date (YYYY-MM-DD, user time zone) = that day at noon, or at time (HH:mm); at = exact ISO instant; dates = several days, one outing per day (a multi-day meetup, like the app). Updates each person\'s last-seen date. Use list_people first to get ids; to add people to an existing outing use update_outing.',
  input: {
    personIds: z.array(docIdSchema).min(1).max(50).describe('People who were there.'),
    date: dayString.optional().describe('Day of the meeting (default: now).'),
    dates: z.array(dayString).min(1).max(60).optional().describe('Several days: one outing per day, everyone on each.'),
    time: timeString.optional().describe('Time of day (default: noon when a date is given).'),
    at: instantString.optional().describe('Exact instant instead of date/time.'),
    note: z.string().max(5000).optional().describe('Shared description of the outing.'),
  },
  handler: async (args, ctx) => {
    if (args.at !== undefined && (args.date !== undefined || args.dates !== undefined || args.time !== undefined)) throw new ToolError('Pass either at or date(s)/time, not both');
    if (args.date !== undefined && args.dates !== undefined) throw new ToolError('Pass either date or dates, not both');
    const ids = [...new Set(args.personIds)];
    const [people, existing, clk] = await Promise.all([requirePeople(ctx, ids), ctx.store.all('checkIns'), clock(ctx)]);
    let instants: number[];
    if (args.at !== undefined) instants = [parseInstant(args.at)];
    else if (args.dates !== undefined) {
      const days = [...new Set(args.dates)].sort().map((d) => requireLocalDate(d, 'dates'));
      instants = days.map((d) => atOn(clk, d, args.time));
    } else if (args.date !== undefined || args.time !== undefined) instants = [atOn(clk, args.date ? requireLocalDate(args.date, 'date') : clk.today, args.time)];
    else instants = [clk.nowMs];
    const note = cleanText(args.note);
    const batch = ctx.store.batch();
    const created: Doc[] = [];
    const names = byId(people);
    const outings = instants.map((at) => {
      const outingId = ctx.newId();
      const rows = ids.map((personId) => {
        const id = ctx.newId();
        const data = { personId, at, note, outingId };
        batch.create('checkIns', id, data);
        created.push({ id, ...data });
        return { id, personId, name: nameOf(names, personId) };
      });
      return { outingId, date: formatLocalDate(localDateOf(at, clk.tz)), checkIns: rows };
    });
    refreshLastCheckIn(batch, people, [...existing, ...created], ids);
    await batch.commit();
    return { created: true, ...outings[0], outings: outings.length > 1 ? outings : undefined };
  },
});

export const listOutings = defineTool({
  name: 'list_outings',
  title: 'List outings',
  description: 'Outings history, most recent first: check-ins grouped by outingId with date, shared note and attendees (with their checkInId). Optional period (from/to inclusive, YYYY-MM-DD in the user time zone) and personId (outings that person attended).',
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

export const listCheckIns = defineTool({
  name: 'list_check_ins',
  title: 'List check-ins',
  description: 'Individual check-ins (one row per person per outing), most recent first, with id, person, date, instant, note, outingId and who else was there. Filter by personId (their full history), outingId and period (from/to inclusive, YYYY-MM-DD).',
  input: {
    personId: docIdSchema.optional(),
    outingId: docIdSchema.optional(),
    from: dayString.optional(),
    to: dayString.optional(),
    ...pagination,
  },
  readOnly: true,
  handler: async (args, ctx) => {
    const [checkIns, people, clk] = await Promise.all([ctx.store.all('checkIns'), ctx.store.all('people'), clock(ctx)]);
    const from = args.from ? toEpochDay(requireLocalDate(args.from, 'from')) : -Infinity;
    const to = args.to ? toEpochDay(requireLocalDate(args.to, 'to')) : Infinity;
    if (from > to) throw new ToolError('from is after to');
    const peopleById = byId(people);
    const rows = checkIns
      .filter((c) => !args.personId || str(c.personId) === args.personId)
      .filter((c) => !args.outingId || str(c.outingId) === args.outingId)
      .filter((c) => {
        const d = toEpochDay(localDateOf(num(c.at) ?? 0, clk.tz));
        return d >= from && d <= to;
      })
      .sort((a, b) => (num(b.at) ?? 0) - (num(a.at) ?? 0))
      .map((c) => {
        const view = checkInView(c, clk.tz);
        const companions = view.outingId ? checkIns.filter((o) => str(o.outingId) === view.outingId && o.id !== c.id).map((o) => ({ personId: str(o.personId), name: nameOf(peopleById, str(o.personId)) })) : [];
        return { ...view, personName: nameOf(peopleById, view.personId), with: companions };
      });
    return { timeZone: clk.tz, ...page(rows, args.limit, args.offset) };
  },
});

export const updateCheckIn = defineTool({
  name: 'update_check_in',
  title: 'Edit one check-in',
  description:
    'Edits ONE person\'s check-in (from list_check_ins, get_person or list_outings attendees), like editing it from their history in the app: at (ISO instant), or date (noon, or time) and/or time (keeps the day), note (null clears), personId (reassign). If the row belongs to an outing shared with others it is detached into an outing of its own, like the app, unless keepOuting is true; outingId moves it into the given outing. Last-seen dates are recomputed. To edit the whole outing use update_outing.',
  input: {
    checkInId: docIdSchema,
    at: instantString.optional(),
    date: dayString.optional(),
    time: timeString.optional(),
    note: z.string().max(5000).nullable().optional(),
    personId: docIdSchema.optional(),
    keepOuting: z.boolean().default(false).describe('Stay in the shared outing instead of being detached.'),
    outingId: docIdSchema.optional().describe('Move the check-in into this outing (e.g. an existing one from list_outings).'),
  },
  idempotent: true,
  handler: async (args, ctx) => {
    if (args.at !== undefined && (args.date !== undefined || args.time !== undefined)) throw new ToolError('Pass either at or date/time, not both');
    const [all, clk] = await Promise.all([ctx.store.all('checkIns'), clock(ctx)]);
    const row = all.find((c) => c.id === args.checkInId);
    if (!row) throw new ToolError(`Check-in not found: ${args.checkInId} (use list_check_ins to get ids)`);
    const oldPerson = str(row.personId);
    const newPerson = args.personId ?? oldPerson;
    const people = await requirePeople(ctx, newPerson === oldPerson ? [] : [newPerson]);
    const patch: Record<string, unknown> = {};
    const current = num(row.at) ?? clk.nowMs;
    if (args.at !== undefined) patch.at = parseInstant(args.at);
    else if (args.date !== undefined) patch.at = atOn(clk, requireLocalDate(args.date, 'date'), args.time);
    else if (args.time !== undefined) patch.at = atOn(clk, localDateOf(current, clk.tz), args.time);
    if (args.note !== undefined) patch.note = cleanText(args.note);
    if (newPerson !== oldPerson) patch.personId = newPerson;
    const hasEdit = Object.keys(patch).length > 0 || args.outingId !== undefined;
    if (!hasEdit) throw new ToolError('Nothing to update: pass at least one field');
    const oldOuting = str(row.outingId);
    if (args.outingId !== undefined) patch.outingId = args.outingId;
    else if (!args.keepOuting) {
      const shared = oldOuting !== '' && all.some((c) => c.id !== row.id && str(c.outingId) === oldOuting && str(c.personId) !== oldPerson);
      if (shared || oldOuting === '') patch.outingId = ctx.newId();
    }
    const batch = ctx.store.batch().update('checkIns', row.id, patch);
    const updated = { ...row, ...patch };
    refreshLastCheckIn(batch, people, all.map((c) => (c.id === row.id ? updated : c)), [oldPerson, newPerson]);
    await batch.commit();
    return { updated: true, checkInId: row.id, fields: Object.keys(patch), detached: 'outingId' in patch && args.outingId === undefined, checkIn: checkInView(updated, clk.tz) };
  },
});

export const deleteCheckIn = defineTool({
  name: 'delete_check_in',
  title: 'Delete one check-in',
  description: 'Removes ONE person\'s check-in (tombstone): the other attendees of the outing keep theirs. The person\'s last-seen date is recomputed from the remaining history. Only on explicit request; for a whole outing use delete_outing.',
  input: { checkInId: docIdSchema },
  destructive: true,
  handler: async ({ checkInId }, ctx) => {
    const [all, people] = await Promise.all([ctx.store.all('checkIns'), ctx.store.all('people')]);
    const row = all.find((c) => c.id === checkInId);
    if (!row) throw new ToolError(`Check-in not found: ${checkInId} (use list_check_ins to get ids)`);
    const batch = ctx.store.batch().tombstone('checkIns', checkInId);
    refreshLastCheckIn(batch, people, all.filter((c) => c.id !== checkInId), [str(row.personId)]);
    await batch.commit();
    return { deleted: true, checkInId, personId: str(row.personId), outingId: str(row.outingId) || null };
  },
});

export const updateOuting = defineTool({
  name: 'update_outing',
  title: 'Edit an outing',
  description:
    'Rewrites an outing as a whole, like the app: new moment (date at noon or time, time alone keeps the day, or at = ISO instant), new shared note (null clears it) and attendees: personIds = full list, or addPersonIds/removePersonIds (people removed lose their check-in, people added get one; at least one must remain, otherwise use delete_outing). Everyone touched has their last-seen date recomputed. Fields not given stay as they are.',
  input: {
    outingId: docIdSchema,
    date: dayString.optional(),
    time: timeString.optional(),
    at: instantString.optional(),
    note: z.string().max(5000).nullable().optional(),
    personIds: z.array(docIdSchema).min(1).max(50).optional().describe('Full list of attendees (replaces the current one).'),
    addPersonIds: z.array(docIdSchema).min(1).max(50).optional().describe('People to add.'),
    removePersonIds: z.array(docIdSchema).min(1).max(50).optional().describe('People to remove.'),
  },
  destructive: true,
  handler: async (args, ctx) => {
    if (args.at !== undefined && (args.date !== undefined || args.time !== undefined)) throw new ToolError('Pass either at or date/time, not both');
    if (args.personIds && (args.addPersonIds || args.removePersonIds)) throw new ToolError('Pass either personIds (full list) or addPersonIds/removePersonIds, not both');
    const [{ all, rows }, clk] = await Promise.all([outingRows(ctx, args.outingId), clock(ctx)]);
    const currentAt = Math.max(...rows.map((r) => num(r.at) ?? 0));
    let at = currentAt;
    if (args.at !== undefined) at = parseInstant(args.at);
    else if (args.date || args.time) at = atOn(clk, args.date ? requireLocalDate(args.date, 'date') : localDateOf(currentAt, clk.tz), args.time);
    const note = args.note === undefined ? (rows.map((r) => cleanText(str(r.note))).find((n) => n !== null) ?? null) : cleanText(args.note);
    const before = rows.map((r) => str(r.personId));
    let wanted: string[];
    if (args.personIds) wanted = [...new Set(args.personIds)];
    else {
      const remove = new Set(args.removePersonIds ?? []);
      const notThere = [...remove].filter((id) => !before.includes(id));
      if (notThere.length) throw new ToolError(`Not in this outing: ${notThere.join(', ')}`);
      wanted = [...new Set([...before, ...(args.addPersonIds ?? [])])].filter((id) => !remove.has(id));
    }
    if (wanted.length === 0) throw new ToolError('An outing needs at least one person: use delete_outing to remove it');
    const people = await requirePeople(ctx, wanted.filter((id) => !before.includes(id)));

    const batch = ctx.store.batch();
    const keptRowIds = new Set<string>();
    const after: Doc[] = all.filter((c) => str(c.outingId) !== args.outingId);
    const added: string[] = [];
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
        added.push(personId);
      }
    }
    const removed = rows.filter((r) => !keptRowIds.has(r.id));
    for (const r of removed) batch.tombstone('checkIns', r.id);
    refreshLastCheckIn(batch, people, after, [...before, ...wanted]);
    await batch.commit();
    return {
      updated: true,
      outingId: args.outingId,
      date: formatLocalDate(localDateOf(at, clk.tz)),
      note,
      attendees: wanted.length,
      added: added.length,
      removedCheckIns: removed.length,
    };
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
