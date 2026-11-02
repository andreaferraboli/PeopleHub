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
  'Use ids, never names, in write tools: look them up first with list_people, list_outings, list_events or list_reminders.',
  'Deletes are tombstones synced to the phone. Destructive tools (delete_*, update_outing removing attendees) only on explicit request.',
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
  events.listEvents,
  reminders.listReminders,
  settings.getSettings,
  raw.rawRead,
  // scrittura
  people.createPerson,
  people.updatePerson,
  people.deletePerson,
  outings.checkIn,
  outings.updateOuting,
  outings.deleteOuting,
  events.createEvent,
  events.updateEvent,
  events.deleteEvent,
  reminders.createReminder,
  reminders.updateReminder,
  reminders.deleteReminder,
  reminders.markReminderDone,
  settings.updateSettings,
];

export function buildMcpServer(ctx: ToolContext): McpServer {
  const server = new McpServer({ name: 'peoplehub', version: '1.0.0' }, { instructions: SERVER_INSTRUCTIONS });
  for (const register of ALL_TOOLS) register(server, ctx);
  return server;
}
