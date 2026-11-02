/**
 * Tool sulle persone: profilo, elenco e ricerca, dettaglio, tag, check-in in ritardo, compleanni,
 * creazione, modifica e cancellazione (tombstone).
 */
import { z } from 'zod';
import { compareUrgency, type Threshold } from '../domain/checkins.js';
import { formatLocalDate, parseLocalDate, requireLocalDate } from '../domain/dates.js';
import { ToolError } from '../domain/errors.js';
import type { Doc } from '../store.js';
import { clock, defineTool, docIdSchema, dayString, page, pagination } from './helpers.js';
import {
  birthdayInfo,
  byId,
  checkInView,
  eventView,
  fullName,
  globalThreshold,
  interestsOf,
  isTracked,
  lastCheckInByPerson,
  lastSeenMs,
  loadSettings,
  personRow,
  reminderView,
  requirePerson,
  str,
  strArray,
  type Interest,
} from './views.js';

const interestSchema = z.object({ key: z.string().max(200), value: z.string().max(2000) });

/** Tag ripuliti come UpsertPersonUseCase: trim, niente vuoti, niente doppioni. */
export function cleanTags(tags: string[]): string[] {
  return [...new Set(tags.map((t) => t.trim()).filter(Boolean))];
}

/** Interessi: si scartano le righe con chiave e valore entrambi vuoti. */
export function cleanInterests(list: Interest[]): Interest[] {
  return list.filter((i) => i.key.trim() !== '' || i.value.trim() !== '').map((i) => ({ key: i.key.trim(), value: i.value.trim() }));
}

/** Il compleanno si salva nel formato di Room (ISO_LOCAL_DATE). */
function birthdayField(value: string | null | undefined): string | null | undefined {
  if (value === undefined) return undefined;
  if (value === null || value.trim() === '') return null;
  return formatLocalDate(requireLocalDate(value, 'birthday'));
}

function thresholdFields(warning: number | null | undefined, critical: number | null | undefined): Partial<Threshold> | { warningDays: null; criticalDays: null } | undefined {
  if (warning === undefined && critical === undefined) return undefined;
  if (warning === null || critical === null) {
    if (warning === null && critical === null) return { warningDays: null, criticalDays: null };
    throw new ToolError('Set both warningDays and criticalDays, or both to null to use the global thresholds');
  }
  if (warning === undefined || critical === undefined) throw new ToolError('warningDays and criticalDays go together: pass both');
  if (warning < 1 || critical <= warning) throw new ToolError('Need warningDays >= 1 and criticalDays > warningDays');
  return { warningDays: warning, criticalDays: critical };
}

const loadAll = async (ctx: Parameters<typeof clock>[0]) => {
  const [people, checkIns, settings, clk] = await Promise.all([ctx.store.all('people'), ctx.store.all('checkIns'), loadSettings(ctx), clock(ctx)]);
  return { people, checkIns, settings, clk, last: lastCheckInByPerson(checkIns) };
};

/* ------------------------------------------------------------------ lettura */

export const getProfile = defineTool({
  name: 'get_profile',
  title: 'Profile and counts',
  description: 'Who is signed in and how much data there is: number of people (family, birthday-only, tracked), check-ins, outings, events, reminders, plus the time zone and today\'s date used for all date maths.',
  input: {},
  readOnly: true,
  handler: async (_args, ctx) => {
    const [{ people, checkIns, clk }, events, reminders] = await Promise.all([loadAll(ctx), ctx.store.all('events'), ctx.store.all('reminders')]);
    return {
      uid: ctx.uid,
      email: ctx.email,
      timeZone: clk.tz,
      today: clk.todayIso,
      counts: {
        people: people.length,
        family: people.filter((p) => p.isFamily === true).length,
        birthdayOnly: people.filter((p) => p.birthdayOnly === true).length,
        trackedForCheckIns: people.filter(isTracked).length,
        withBirthday: people.filter((p) => parseLocalDate(p.birthday)).length,
        checkIns: checkIns.length,
        outings: new Set(checkIns.map((c) => str(c.outingId) || c.id)).size,
        events: events.length,
        reminders: reminders.length,
        activeReminders: reminders.filter((r) => r.enabled !== false).length,
      },
    };
  },
});

/** Ricerca come la FTS dell'app: ogni parola deve essere l'inizio di una parola in nome, note, tag o interessi. */
export function matchesQuery(p: Doc, query: string): boolean {
  const tokens = query.toLowerCase().split(/[^\p{L}\p{N}]+/u).filter(Boolean);
  if (tokens.length === 0) return true;
  const haystack = [str(p.firstName), str(p.lastName), str(p.notes), ...strArray(p.tags), ...interestsOf(p.interests).flatMap((i) => [i.key, i.value])]
    .join(' ')
    .toLowerCase()
    .split(/[^\p{L}\p{N}]+/u)
    .filter(Boolean);
  return tokens.every((t) => haystack.some((w) => w.startsWith(t)));
}

export const listPeople = defineTool({
  name: 'list_people',
  title: 'List and search people',
  description:
    'Lists people with optional search and filters. query matches word prefixes in first/last name, notes, tags and interests (all words must match). tags keeps people having ANY of the given tags. family/birthdayOnly: "all" (default), "only" or "exclude". sort: name (default), lastCheckIn (never seen / longest ago first) or upcomingBirthday. Each row has the last check-in date, days since, check-in status (FRESH/DUE/OVERDUE/NEVER, null when not tracked) and the next birthday distance.',
  input: {
    query: z.string().max(200).optional().describe('Free-text search.'),
    tags: z.array(z.string()).max(20).optional().describe('Keep people with any of these tags (exact match, case-sensitive like the app).'),
    family: z.enum(['all', 'only', 'exclude']).default('all'),
    birthdayOnly: z.enum(['all', 'only', 'exclude']).default('all').describe('Birthday-only entries are bare birthdays, hidden from the app directory.'),
    sort: z.enum(['name', 'lastCheckIn', 'upcomingBirthday']).default('name'),
    ...pagination,
  },
  readOnly: true,
  handler: async (args, ctx) => {
    const { people, settings, clk, last } = await loadAll(ctx);
    const tri = (mode: 'all' | 'only' | 'exclude', flag: boolean) => mode === 'all' || (mode === 'only' ? flag : !flag);
    const rows = people
      .filter((p) => !args.query || matchesQuery(p, args.query))
      .filter((p) => !args.tags?.length || strArray(p.tags).some((t) => args.tags!.includes(t)))
      .filter((p) => tri(args.family, p.isFamily === true) && tri(args.birthdayOnly, p.birthdayOnly === true))
      .map((p) => personRow(p, lastSeenMs(p, last), clk, settings));
    const byName = (a: { name: string }, b: { name: string }) => a.name.localeCompare(b.name, 'it', { sensitivity: 'base' });
    if (args.sort === 'name') rows.sort(byName);
    // come PeopleSort.LAST_CHECK_IN: mai visti prima, poi chi e' stato visto piu tempo fa
    else if (args.sort === 'lastCheckIn') rows.sort((a, b) => (b.daysSinceLastCheckIn ?? Number.MAX_SAFE_INTEGER) - (a.daysSinceLastCheckIn ?? Number.MAX_SAFE_INTEGER) || byName(a, b));
    else rows.sort((a, b) => (a.daysUntilBirthday ?? Number.MAX_SAFE_INTEGER) - (b.daysUntilBirthday ?? Number.MAX_SAFE_INTEGER) || byName(a, b));
    return { timeZone: clk.tz, today: clk.todayIso, ...page(rows, args.limit, args.offset) };
  },
});

export const getPerson = defineTool({
  name: 'get_person',
  title: 'Person details',
  description: 'Full detail of one person: notes, tags, interests, birthday (next occurrence, age), check-in status and thresholds, the latest check-ins (with outing id and companions), their reminders and linked events.',
  input: {
    id: docIdSchema.describe('Person id (from list_people).'),
    checkInLimit: z.number().int().min(1).max(200).default(10).describe('How many recent check-ins to include.'),
  },
  readOnly: true,
  handler: async ({ id, checkInLimit }, ctx) => {
    const [{ people, checkIns, settings, clk, last }, events, reminders, completions] = await Promise.all([
      loadAll(ctx),
      ctx.store.all('events'),
      ctx.store.all('reminders'),
      ctx.store.all('reminderCompletions'),
    ]);
    const p = people.find((x) => x.id === id);
    if (!p) throw new ToolError(`Person not found: ${id} (use list_people to get ids)`);
    const peopleById = byId(people);
    const mine = checkIns.filter((c) => str(c.personId) === id).sort((a, b) => (b.at as number) - (a.at as number));
    const recent = mine.slice(0, checkInLimit).map((c) => {
      const view = checkInView(c, clk.tz);
      const companions = view.outingId
        ? checkIns.filter((o) => str(o.outingId) === view.outingId && str(o.personId) !== id).map((o) => ({ personId: str(o.personId), name: peopleById.get(str(o.personId)) ? fullName(peopleById.get(str(o.personId))!) : '(deleted person)' }))
        : [];
      return { ...view, with: companions };
    });
    return {
      ...personRow(p, lastSeenMs(p, last), clk, settings),
      notes: str(p.notes),
      interests: interestsOf(p.interests),
      birthdayDetails: birthdayInfo(p.birthday, clk),
      hasPhoto: p.hasPhoto === true,
      createdAt: typeof p.createdAt === 'number' ? new Date(p.createdAt).toISOString() : null,
      checkInCount: mine.length,
      recentCheckIns: recent,
      reminders: reminders.filter((r) => str(r.personId) === id).map((r) => reminderView(r, clk, peopleById, completions)).sort((a, b) => a.nextFireAt.localeCompare(b.nextFireAt)),
      events: events.filter((e) => str(e.personId) === id).map((e) => eventView(e, clk, peopleById)).sort((a, b) => a.at.localeCompare(b.at)),
    };
  },
});

export const listTags = defineTool({
  name: 'list_tags',
  title: 'List tags',
  description: 'Every distinct tag used on people, with how many people carry it (sorted by name).',
  input: {},
  readOnly: true,
  handler: async (_args, ctx) => {
    const counts = new Map<string, number>();
    for (const p of await ctx.store.all('people')) for (const t of new Set(strArray(p.tags))) counts.set(t, (counts.get(t) ?? 0) + 1);
    return { tags: [...counts.entries()].map(([tag, people]) => ({ tag, people })).sort((a, b) => a.tag.localeCompare(b.tag, 'it', { sensitivity: 'base' })) };
  },
});

export const listOverduePeople = defineTool({
  name: 'list_overdue_people',
  title: 'People to see again',
  description:
    'The app\'s "urgent check-ins": tracked people (not family, not check-in disabled, not birthday-only) whose days since the last check-in reached the warning threshold. Each person uses their own thresholds when set, otherwise the global ones from settings; warningDays/criticalDays here override the global ones for this query only. Status DUE = past warning, OVERDUE = past critical, NEVER = never checked in. Sorted most urgent first.',
  input: {
    minStatus: z.enum(['DUE', 'OVERDUE']).default('DUE').describe('DUE includes DUE, OVERDUE and NEVER; OVERDUE only OVERDUE and NEVER.'),
    includeNever: z.boolean().default(true).describe('Include people never checked in.'),
    warningDays: z.number().int().min(1).max(3650).optional().describe('Global warning threshold override (days).'),
    criticalDays: z.number().int().min(2).max(3650).optional().describe('Global critical threshold override (days).'),
    ...pagination,
  },
  readOnly: true,
  handler: async (args, ctx) => {
    const { people, settings, clk, last } = await loadAll(ctx);
    const base = globalThreshold(settings);
    const warning = args.warningDays ?? base.warningDays;
    const critical = args.criticalDays ?? Math.max(base.criticalDays, warning + 1);
    if (critical <= warning) throw new ToolError('criticalDays must be greater than warningDays');
    const effective = { ...settings, warningDays: warning, criticalDays: critical };
    const rows = people
      .filter(isTracked)
      .map((p) => personRow(p, lastSeenMs(p, last), clk, effective))
      .filter((r) => r.checkInStatus !== null && r.checkInStatus !== 'FRESH')
      .filter((r) => (args.minStatus === 'OVERDUE' ? r.checkInStatus !== 'DUE' : true))
      .filter((r) => args.includeNever || r.checkInStatus !== 'NEVER')
      .sort((a, b) => compareUrgency({ status: a.checkInStatus!, daysSince: a.daysSinceLastCheckIn }, { status: b.checkInStatus!, daysSince: b.daysSinceLastCheckIn }));
    return { today: clk.todayIso, globalThreshold: { warningDays: warning, criticalDays: critical }, ...page(rows, args.limit, args.offset) };
  },
});

export const upcomingBirthdays = defineTool({
  name: 'upcoming_birthdays',
  title: 'Upcoming birthdays',
  description: 'Birthdays falling within the next withinDays days (today = 0), soonest first, with days left, the date and the age turned (null when the birth year is unknown). Feb 29 birthdays fall on Feb 28 in non-leap years, like the app. Includes family and birthday-only entries.',
  input: {
    withinDays: z.number().int().min(0).max(366).default(30),
    ...pagination,
  },
  readOnly: true,
  handler: async (args, ctx) => {
    const [people, clk] = await Promise.all([ctx.store.all('people'), clock(ctx)]);
    const rows = people
      .map((p) => ({ p, b: birthdayInfo(p.birthday, clk) }))
      .filter((x): x is { p: Doc; b: NonNullable<ReturnType<typeof birthdayInfo>> } => x.b !== null && x.b.daysUntil <= args.withinDays)
      .map(({ p, b }) => ({ personId: p.id, name: fullName(p), ...b, isFamily: p.isFamily === true, birthdayOnly: p.birthdayOnly === true }))
      .sort((a, b) => a.daysUntil - b.daysUntil || a.name.localeCompare(b.name, 'it', { sensitivity: 'base' }));
    return { today: clk.todayIso, timeZone: clk.tz, withinDays: args.withinDays, ...page(rows, args.limit, args.offset) };
  },
});

/* ------------------------------------------------------------------ scrittura */

const personFields = {
  lastName: z.string().max(200).optional(),
  birthday: dayString.nullable().optional().describe('Birthday YYYY-MM-DD (null to clear).'),
  notes: z.string().max(20_000).optional(),
  tags: z.array(z.string().max(100)).max(100).optional().describe('Full tag list (replaces the existing one).'),
  interests: z.array(interestSchema).max(200).optional().describe('Full list of {key, value} interests (replaces the existing one).'),
  warningDays: z.number().int().min(1).max(3650).nullable().optional().describe('Per-person warning threshold (days); null together with criticalDays = use global.'),
  criticalDays: z.number().int().min(2).max(3650).nullable().optional().describe('Per-person critical threshold (days).'),
  notificationsEnabled: z.boolean().optional().describe('Check-in reminder notifications for this person.'),
  birthdayOnly: z.boolean().optional().describe('Bare birthday entry, not a tracked relationship.'),
  checkInDisabled: z.boolean().optional().describe('Exclude from check-in tracking.'),
  isFamily: z.boolean().optional().describe('Family member: no check-in tracking, shown as "Family".'),
};

export const createPerson = defineTool({
  name: 'create_person',
  title: 'Create a person',
  description: 'Adds a person. Only firstName is required. Tags are trimmed and de-duplicated, empty interests are dropped. New people have notifications off, like in the app.',
  input: { firstName: z.string().min(1).max(200), ...personFields },
  handler: async (args, ctx) => {
    const first = args.firstName.trim();
    if (!first) throw new ToolError('firstName is required');
    const t = thresholdFields(args.warningDays, args.criticalDays);
    const id = ctx.newId();
    const data = {
      firstName: first,
      lastName: (args.lastName ?? '').trim(),
      birthday: birthdayField(args.birthday) ?? null,
      notes: args.notes ?? '',
      tags: cleanTags(args.tags ?? []),
      interests: cleanInterests(args.interests ?? []),
      warningDays: t?.warningDays ?? null,
      criticalDays: t?.criticalDays ?? null,
      notificationsEnabled: args.notificationsEnabled ?? false,
      birthdayOnly: args.birthdayOnly ?? false,
      checkInDisabled: args.checkInDisabled ?? false,
      isFamily: args.isFamily ?? false,
      createdAt: ctx.now().getTime(),
      lastCheckInAt: null,
      hasPhoto: false,
    };
    await ctx.store.batch().create('people', id, data).commit();
    return { created: true, id, name: `${data.firstName} ${data.lastName}`.trim() };
  },
});

export const updatePerson = defineTool({
  name: 'update_person',
  title: 'Update a person',
  description: 'Changes only the given fields of a person. tags and interests replace the whole list. birthday null clears it; warningDays+criticalDays both null go back to the global thresholds.',
  input: { id: docIdSchema, firstName: z.string().min(1).max(200).optional(), ...personFields },
  idempotent: true,
  handler: async (args, ctx) => {
    await requirePerson(ctx, args.id);
    const t = thresholdFields(args.warningDays, args.criticalDays);
    const patch: Record<string, unknown> = {};
    if (args.firstName !== undefined) {
      if (!args.firstName.trim()) throw new ToolError('firstName cannot be empty');
      patch.firstName = args.firstName.trim();
    }
    if (args.lastName !== undefined) patch.lastName = args.lastName.trim();
    const birthday = birthdayField(args.birthday);
    if (birthday !== undefined) patch.birthday = birthday;
    if (args.notes !== undefined) patch.notes = args.notes;
    if (args.tags !== undefined) patch.tags = cleanTags(args.tags);
    if (args.interests !== undefined) patch.interests = cleanInterests(args.interests);
    if (t) Object.assign(patch, t);
    for (const k of ['notificationsEnabled', 'birthdayOnly', 'checkInDisabled', 'isFamily'] as const) if (args[k] !== undefined) patch[k] = args[k];
    if (Object.keys(patch).length === 0) throw new ToolError('Nothing to update: pass at least one field');
    await ctx.store.batch().update('people', args.id, patch).commit();
    return { updated: true, id: args.id, fields: Object.keys(patch) };
  },
});

export const deletePerson = defineTool({
  name: 'delete_person',
  title: 'Delete a person',
  description: 'Deletes a person like the app does: the person, their check-ins and their reminders (with completion history) are marked deleted (tombstones, synced to the phone); events linked to them are kept but unlinked. Other attendees of shared outings keep their own check-ins. Only on explicit request.',
  input: { id: docIdSchema },
  destructive: true,
  handler: async ({ id }, ctx) => {
    const person = await requirePerson(ctx, id);
    const [checkIns, reminders, completions, events] = await Promise.all([ctx.store.all('checkIns'), ctx.store.all('reminders'), ctx.store.all('reminderCompletions'), ctx.store.all('events')]);
    const batch = ctx.store.batch().tombstone('people', id);
    const myCheckIns = checkIns.filter((c) => str(c.personId) === id);
    const myReminders = reminders.filter((r) => str(r.personId) === id);
    const reminderIds = new Set(myReminders.map((r) => r.id));
    const myCompletions = completions.filter((c) => reminderIds.has(str(c.reminderId)));
    const myEvents = events.filter((e) => str(e.personId) === id);
    for (const c of myCheckIns) batch.tombstone('checkIns', c.id);
    for (const r of myReminders) batch.tombstone('reminders', r.id);
    for (const c of myCompletions) batch.tombstone('reminderCompletions', c.id);
    for (const e of myEvents) batch.update('events', e.id, { personId: null });
    await batch.commit();
    return { deleted: true, id, name: fullName(person), checkInsDeleted: myCheckIns.length, remindersDeleted: myReminders.length, eventsUnlinked: myEvents.length };
  },
});
