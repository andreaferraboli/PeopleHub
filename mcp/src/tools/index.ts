/**
 * Registro dei tool MCP di PeopleHub.
 */
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import type { ToolContext } from './helpers.js';
import * as people from './people.js';
import * as outings from './outings.js';
import * as events from './events.js';
import * as reminders from './reminders.js';
import * as settings from './settings.js';
import * as raw from './raw.js';

export const SERVER_INSTRUCTIONS = [
  'PeopleHub: the user\'s personal relationship hub (people, birthdays, check-ins grouped into outings, interests, personal events, irregular-cadence reminders). Data is synced with the PeopleHub Android app.',
  'Dates are ISO (YYYY-MM-DD) in the user time zone from settings (default Europe/Rome); instants are ISO 8601 with offset.',
  'Use ids, never names, in write tools: look them up first with list_people, list_outings, list_check_ins, list_events, list_reminders or list_reminder_completions.',
  'Outings: check_in creates one (one check-in per person, shared outingId); update_outing edits it as a whole (moment, note, attendees); update_check_in / delete_check_in touch one person\'s row.',
  'Reminders keep their full done history in list_reminder_completions: add_reminder_completion (also past dates), update_reminder_completion and delete_reminder_completion edit it and recompute the next date like the app.',
  'Deletes are tombstones synced to the phone. Destructive tools (delete_*, update_outing removing attendees, raw_write) only on explicit request. raw_write/raw_read on users/{uid}/ are the fallback for anything no tool covers.',
].join('\n');

export const ALL_TOOLS = [
  // lettura
  people.getProfile,
  people.listPeople,
  people.getPerson,
  people.listTags,
  people.listOverduePeople,
  people.upcomingBirthdays,
  outings.listOutings,
  outings.listCheckIns,
  events.listEvents,
  reminders.listReminders,
  reminders.listReminderCompletions,
  settings.getSettings,
  raw.rawRead,
  // scrittura
  people.createPerson,
  people.updatePerson,
  people.deletePerson,
  outings.checkIn,
  outings.updateOuting,
  outings.deleteOuting,
  outings.updateCheckIn,
  outings.deleteCheckIn,
  events.createEvent,
  events.updateEvent,
  events.deleteEvent,
  reminders.createReminder,
  reminders.updateReminder,
  reminders.deleteReminder,
  reminders.markReminderDone,
  reminders.addReminderCompletion,
  reminders.updateReminderCompletion,
  reminders.deleteReminderCompletion,
  settings.updateSettings,
  raw.rawWrite,
];

export function buildMcpServer(ctx: ToolContext): McpServer {
  const server = new McpServer({ name: 'peoplehub', version: '1.0.0' }, { instructions: SERVER_INSTRUCTIONS });
  for (const register of ALL_TOOLS) register(server, ctx);
  return server;
}
