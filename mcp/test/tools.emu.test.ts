/**
 * Tool contro l'EMULATORE di Firestore (progetto demo-ph-mcp, mai dati veri). Si lancia con `npm run test:emu`;
 * senza FIRESTORE_EMULATOR_HOST la suite si salta. Controlla anche il contratto docs/firestore-schema.md:
 * campi di sync, tombstone, letture che escludono i deleted.
 */
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { Timestamp } from '../src/firestore/index.js';
import { EMULATOR, makeHarness, testDb, type Harness } from './helpers.js';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const NOW = Date.parse('2026-11-02T10:00:00Z');
const DAY = 86_400_000;

describe.skipIf(!EMULATOR)('tool MCP contro Firestore emulator', () => {
  let h: Harness;
  const opened: Harness[] = [];
  beforeEach(async () => {
    h = await makeHarness();
    opened.push(h);
  });
  afterAll(async () => {
    for (const x of opened) await x.close();
  });

  const person = async (args: Record<string, unknown>) => (await h.call('create_person', args)).id as string;

  it('create_person scrive il documento del contratto con i campi di sync', async () => {
    const id = await person({ firstName: ' Giulia ', lastName: 'Rossi', birthday: '1994-11-03', tags: [' amici', 'amici', '', 'calcio'], interests: [{ key: 'Cibo', value: 'Sushi' }, { key: ' ', value: '' }] });
    expect(id).toMatch(UUID);
    const doc = (await h.doc(`people/${id}`))!;
    expect(doc).toMatchObject({
      firstName: 'Giulia',
      lastName: 'Rossi',
      birthday: '1994-11-03',
      notes: '',
      tags: ['amici', 'calcio'],
      interests: [{ key: 'Cibo', value: 'Sushi' }],
      warningDays: null,
      criticalDays: null,
      notificationsEnabled: false,
      birthdayOnly: false,
      checkInDisabled: false,
      isFamily: false,
      createdAt: NOW,
      lastCheckInAt: null,
      hasPhoto: false,
      updatedAt: NOW,
      deleted: false,
      updatedBy: 'mcp',
    });
    expect(doc.serverUpdatedAt).toBeInstanceOf(Timestamp);
  });

  it('check_in di piu persone: un outingId UUID condiviso, data a mezzogiorno nel fuso, lastCheckInAt aggiornato', async () => {
    const a = await person({ firstName: 'Anna' });
    const b = await person({ firstName: 'Bruno' });
    const r = await h.call('check_in', { personIds: [a, b], date: '2026-10-30', note: 'Pizza' });
    expect(r.outingId).toMatch(UUID);
    expect(r.checkIns).toHaveLength(2);
    const noon = Date.parse('2026-10-30T11:00:00Z'); // 12:00 a Roma (CET)
    for (const c of r.checkIns) {
      expect(await h.doc(`checkIns/${c.id}`)).toMatchObject({ personId: c.personId, at: noon, note: 'Pizza', outingId: r.outingId, deleted: false, updatedBy: 'mcp' });
    }
    expect((await h.doc(`people/${a}`))!.lastCheckInAt).toBe(noon);
    const outings = await h.call('list_outings', {});
    expect(outings.items[0]).toMatchObject({ outingId: r.outingId, date: '2026-10-30', note: 'Pizza' });
    expect(outings.items[0].attendees.map((x: { name: string }) => x.name)).toEqual(['Anna', 'Bruno']);
    const detail = await h.call('get_person', { id: a });
    expect(detail.recentCheckIns[0].with).toEqual([{ personId: b, name: 'Bruno' }]);
    expect(detail.daysSinceLastCheckIn).toBe(3);
  });

  it('un check-in all indietro non abbassa lastCheckInAt; check_in senza data usa adesso', async () => {
    const a = await person({ firstName: 'Carla' });
    await h.call('check_in', { personIds: [a] });
    expect((await h.doc(`people/${a}`))!.lastCheckInAt).toBe(NOW);
    await h.call('check_in', { personIds: [a], date: '2026-09-01' });
    expect((await h.doc(`people/${a}`))!.lastCheckInAt).toBe(NOW);
  });

  it('update_outing toglie e aggiunge persone e ricalcola lastCheckInAt; delete_outing fa tombstone', async () => {
    const a = await person({ firstName: 'Dario' });
    const b = await person({ firstName: 'Elena' });
    const c = await person({ firstName: 'Fabio' });
    const old = await h.call('check_in', { personIds: [b], date: '2026-10-01' });
    const r = await h.call('check_in', { personIds: [a, b], date: '2026-10-30', note: 'Cena' });
    await h.call('update_outing', { outingId: r.outingId, personIds: [a, c], date: '2026-10-31', note: null });
    const rows = (await testDb().collection(`users/${h.uid}/checkIns`).get()).docs.map((d) => ({ id: d.id, ...d.data() })) as Array<Record<string, unknown>>;
    const outingRows = rows.filter((x) => x.outingId === r.outingId);
    expect(outingRows.filter((x) => x.deleted === true).map((x) => x.personId)).toEqual([b]);
    expect(outingRows.filter((x) => x.deleted === false).map((x) => x.personId).sort()).toEqual([a, c].sort());
    expect(outingRows.filter((x) => x.deleted === false).every((x) => x.note === null && x.at === Date.parse('2026-10-31T11:00:00Z'))).toBe(true);
    // Elena rimossa: torna al check-in precedente
    expect((await h.doc(`people/${b}`))!.lastCheckInAt).toBe(Date.parse('2026-10-01T10:00:00Z'));
    expect((await h.doc(`people/${c}`))!.lastCheckInAt).toBe(Date.parse('2026-10-31T11:00:00Z'));

    await h.call('delete_outing', { outingId: old.outingId });
    expect((await h.doc(`checkIns/${old.checkIns[0].id}`))!.deleted).toBe(true);
    expect((await h.doc(`people/${b}`))!.lastCheckInAt).toBeNull();
    expect((await h.call('list_outings', {})).items.map((o: { outingId: string }) => o.outingId)).toEqual([r.outingId]);
  });

  it('delete_person: tombstone su persona, check-in, promemoria e completamenti; eventi scollegati; letture senza deleted', async () => {
    const a = await person({ firstName: 'Gino' });
    const b = await person({ firstName: 'Ilaria' });
    const out = await h.call('check_in', { personIds: [a, b], date: '2026-10-20' });
    const rem = await h.call('create_reminder', { personId: a, title: 'Chiamare', targetIntervalDays: 7 });
    await h.call('mark_reminder_done', { id: rem.id });
    const ev = await h.call('create_event', { title: 'Compleanno di Gino', date: '2026-12-01', personId: a });
    const res = await h.call('delete_person', { id: a });
    expect(res).toMatchObject({ checkInsDeleted: 1, remindersDeleted: 1, eventsUnlinked: 1 });
    expect(await h.doc(`people/${a}`)).toMatchObject({ deleted: true, firstName: 'Gino', updatedBy: 'mcp' });
    expect((await h.doc(`reminders/${rem.id}`))!.deleted).toBe(true);
    const completions = (await testDb().collection(`users/${h.uid}/reminderCompletions`).get()).docs;
    expect(completions.every((d) => d.data().deleted === true)).toBe(true);
    expect(await h.doc(`events/${ev.id}`)).toMatchObject({ personId: null, deleted: false });
    // Ilaria resta con il suo check-in
    expect((await h.call('list_outings', {})).items[0].attendees.map((x: { personId: string }) => x.personId)).toEqual([b]);
    expect(out.outingId).toBeTruthy();
    expect((await h.call('list_people', {})).items.map((p: { id: string }) => p.id)).toEqual([b]);
    expect(await h.callError('get_person', { id: a })).toMatch(/not found/);
    expect((await h.call('list_reminders', {})).total).toBe(0);
  });

  it('list_people: ricerca per prefisso su nome, note, tag e interessi; filtri e ordinamenti', async () => {
    await person({ firstName: 'Marco', lastName: 'Bianchi', notes: 'Conosciuto in palestra', tags: ['sport'], birthday: '1990-11-10' });
    await person({ firstName: 'Mamma', isFamily: true, birthday: '1965-11-03' });
    await person({ firstName: 'Zia Rosa', birthdayOnly: true, birthday: '1952-02-29' });
    await person({ firstName: 'Luca', interests: [{ key: 'Musica', value: 'Jazz' }], tags: ['lavoro'] });
    const q = async (args: Record<string, unknown>) => (await h.call('list_people', args)).items.map((p: { name: string }) => p.name);
    expect(await q({ query: 'pale' })).toEqual(['Marco Bianchi']);
    expect(await q({ query: 'jaz' })).toEqual(['Luca']);
    expect(await q({ query: 'marco bian' })).toEqual(['Marco Bianchi']);
    expect(await q({ query: 'arco' })).toEqual([]);
    expect(await q({ tags: ['sport', 'lavoro'] })).toEqual(['Luca', 'Marco Bianchi']);
    expect(await q({ family: 'only' })).toEqual(['Mamma']);
    expect(await q({ birthdayOnly: 'exclude', family: 'exclude' })).toEqual(['Luca', 'Marco Bianchi']);
    expect(await q({ sort: 'upcomingBirthday' })).toEqual(['Mamma', 'Marco Bianchi', 'Zia Rosa', 'Luca']);
    const tags = await h.call('list_tags', {});
    expect(tags.tags).toEqual([{ tag: 'lavoro', people: 1 }, { tag: 'sport', people: 1 }]);
  });

  it('list_overdue_people esclude famiglia, check-in disattivati e solo compleanno; soglie per persona', async () => {
    const never = await person({ firstName: 'Mai' });
    const old = await person({ firstName: 'Vecchio' });
    const recent = await person({ firstName: 'Recente' });
    const custom = await person({ firstName: 'Stretto', warningDays: 2, criticalDays: 4 });
    const fam = await person({ firstName: 'Papa', isFamily: true });
    await person({ firstName: 'Spento', checkInDisabled: true });
    await h.call('check_in', { personIds: [old, fam], date: '2026-09-01' });
    await h.call('check_in', { personIds: [recent, custom], date: '2026-10-28' });
    const r = await h.call('list_overdue_people', {});
    expect(r.items.map((p: { id: string; checkInStatus: string }) => [p.id, p.checkInStatus])).toEqual([
      [never, 'NEVER'],
      [old, 'OVERDUE'],
      [custom, 'OVERDUE'],
    ]);
    const strict = await h.call('list_overdue_people', { warningDays: 3, criticalDays: 60, includeNever: false });
    expect(strict.items.map((p: { id: string; checkInStatus: string }) => [p.id, p.checkInStatus])).toEqual([
      [old, 'OVERDUE'],
      [custom, 'OVERDUE'],
      [recent, 'DUE'],
    ]);
  });

  it('upcoming_birthdays: giorni mancanti, eta, 29 febbraio', async () => {
    await person({ firstName: 'Oggi', birthday: '2000-11-02' });
    await person({ firstName: 'Domani', birthday: '1990-11-03' });
    await person({ firstName: 'Lontano', birthday: '1990-06-01' });
    await person({ firstName: 'Bisestile', birthday: '2004-02-29' });
    const r = await h.call('upcoming_birthdays', { withinDays: 120 });
    expect(r.items.map((b: { name: string; daysUntil: number; turningAge: number }) => [b.name, b.daysUntil, b.turningAge])).toEqual([
      ['Oggi', 0, 26],
      ['Domani', 1, 36],
      ['Bisestile', 118, 23],
    ]);
    expect(r.items[2].nextOccurrence).toBe('2027-02-28');
  });

  it('promemoria: creazione con jitter, done con completamento, modifica della cadenza, preset', async () => {
    const a = await person({ firstName: 'Nonna' });
    const rem = await h.call('create_reminder', { personId: a, title: ' Telefonare ', targetIntervalDays: 14, jitterPercent: 20 });
    // random = estremo basso: 11 giorni
    expect(await h.doc(`reminders/${rem.id}`)).toMatchObject({ personId: a, title: 'Telefonare', category: 'CUSTOM', targetIntervalDays: 14, jitterPercent: 20, enabled: true, lastFiredAt: null, nextFireAt: NOW + 11 * DAY, createdAt: NOW, presetKey: null, deleted: false });
    expect(rem.window).toEqual({ minDays: 11, maxDays: 17 });
    const done = await h.call('mark_reminder_done', { id: rem.id });
    expect(done).toMatchObject({ doneOn: '2026-11-02', nextFireDate: '2026-11-13' });
    const comp = await h.doc(`reminderCompletions/${done.completionId}`);
    expect(comp).toMatchObject({ reminderId: rem.id, day: 20759, at: NOW, deleted: false });
    expect(await h.doc(`reminders/${rem.id}`)).toMatchObject({ lastFiredAt: NOW, nextFireAt: NOW + 11 * DAY });
    // titolo: niente nuova data; cadenza: nuova data dalla lastFiredAt
    expect((await h.call('update_reminder', { id: rem.id, title: 'Chiamare' })).rescheduled).toBe(false);
    expect((await h.call('update_reminder', { id: rem.id, targetIntervalDays: 30, jitterPercent: 0 })).rescheduled).toBe(true);
    expect((await h.doc(`reminders/${rem.id}`))!.nextFireAt).toBe(NOW + 30 * DAY);
    const list = await h.call('list_reminders', { withinDays: 40 });
    expect(list.items[0]).toMatchObject({ id: rem.id, timesDone: 1, lastDoneOn: '2026-11-02', daysUntilNext: 30 });
    expect((await h.call('list_reminders', { withinDays: 10 })).total).toBe(0);
    const preset = await h.call('create_reminder', { personId: a, title: 'Nipoti', presetKey: 'family_grandparents' });
    expect(await h.doc(`reminders/${preset.id}`)).toMatchObject({ category: 'FAMILY', targetIntervalDays: 30, jitterPercent: 20, presetKey: 'family_grandparents' });
    await h.call('update_reminder', { id: preset.id, enabled: false });
    expect((await h.call('list_reminders', {})).total).toBe(1);
    expect((await h.call('list_reminders', { includePaused: true })).total).toBe(2);
  });

  it('eventi: creazione con ora nel fuso, filtri, modifica e tombstone', async () => {
    const a = await person({ firstName: 'Sara' });
    const past = await h.call('create_event', { title: 'Laurea', date: '2026-07-15', category: 'Traguardo' });
    const next = await h.call('create_event', { title: 'Viaggio', date: '2026-12-20', time: '08:30', personId: a });
    expect(await h.doc(`events/${next.id}`)).toMatchObject({ title: 'Viaggio', at: Date.parse('2026-12-20T07:30:00Z'), description: null, category: null, personId: a, deleted: false });
    const up = await h.call('list_events', { when: 'upcoming' });
    expect(up.items.map((e: { title: string; daysFromToday: number }) => [e.title, e.daysFromToday])).toEqual([['Viaggio', 48]]);
    expect((await h.call('list_events', { when: 'past' })).items[0]).toMatchObject({ title: 'Laurea', date: '2026-07-15', category: 'Traguardo' });
    await h.call('update_event', { id: next.id, date: '2026-12-21', personId: null });
    expect(await h.doc(`events/${next.id}`)).toMatchObject({ at: Date.parse('2026-12-21T07:30:00Z'), personId: null });
    await h.call('delete_event', { id: past.id });
    expect((await h.doc(`events/${past.id}`))!.deleted).toBe(true);
    expect((await h.call('list_events', {})).total).toBe(1);
  });

  it('impostazioni: default, aggiornamento con coerce e fuso usato per le date', async () => {
    expect(await h.call('get_settings', {})).toMatchObject({ warningDays: 14, criticalDays: 30, birthdayOffsets: [0, 1, 7], dailyReminderHour: 5, timeZone: 'Europe/Rome' });
    const r = await h.call('update_settings', { warningDays: 20, criticalDays: 10, birthdayOffsets: [7, 0, 30], timeZone: 'America/New_York' });
    expect(r.settings).toMatchObject({ warningDays: 20, criticalDays: 21, birthdayOffsets: [0, 7, 30], timeZone: 'America/New_York' });
    expect(await h.doc('settings/main')).toMatchObject({ warningDays: 20, criticalDays: 21, updatedBy: 'mcp', deleted: false });
    expect(await h.callError('update_settings', { birthdayOffsets: [2] })).toMatch(/allowed/);
    expect(await h.callError('update_settings', { timeZone: 'Mars/Base' })).toMatch(/Unknown time zone/);
    expect((await h.call('get_profile', {})).timeZone).toBe('America/New_York');
  });

  it('confini: raw_read solo sotto il proprio uid, id e percorsi validati', async () => {
    await testDb().doc('users/altro-utente/people/x').set({ firstName: 'Segreto', deleted: false });
    expect(await h.callError('raw_read', { path: 'users/altro-utente/people' })).toMatch(/relativ/);
    expect(await h.callError('raw_read', { path: '../altro-utente/people' })).toMatch(/non ammessi/);
    expect(await h.callError('get_person', { id: '../x' })).toMatch(/invalid id/);
    const a = await person({ firstName: 'Tina' });
    const r = await h.call('raw_read', { path: 'people' });
    expect(r.documents.map((d: { id: string }) => d.id)).toEqual([a]);
    expect((await h.call('raw_read', { path: `people/${a}` })).data.serverUpdatedAt).toMatch(/^\d{4}-/);
    // il client confinato rifiuta qualunque percorso fuori da users/{uid}
    await expect(h.db.doc('users/altro-utente/people/x').get()).rejects.toThrow(/non consentito/);
  });

  it('validazioni: persona inesistente, data impossibile, soglie incomplete', async () => {
    expect(await h.callError('check_in', { personIds: ['nope'] })).toMatch(/not found/);
    expect(await h.callError('create_person', { firstName: 'X', birthday: '2026-02-30' })).toMatch(/birthday/);
    expect(await h.callError('create_person', { firstName: 'X', warningDays: 3 })).toMatch(/both/);
    expect(await h.callError('create_person', { firstName: '   ' })).toMatch(/required/);
  });
});
