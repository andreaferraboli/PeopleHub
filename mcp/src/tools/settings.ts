/**
 * Impostazioni sincronizzate (settings/main): soglie globali dei check-in, anticipi dei compleanni,
 * ora del controllo giornaliero e fuso. Quelle solo del telefono (allarmi esatti, lingua, widget) non ci sono.
 */
import { z } from 'zod';
import { coerceGlobalThreshold } from '../domain/checkins.js';
import { isValidTimeZone } from '../domain/dates.js';
import { ToolError } from '../domain/errors.js';
import { defineTool } from './helpers.js';
import { ALLOWED_BIRTHDAY_OFFSETS, loadSettings } from './views.js';

export const getSettings = defineTool({
  name: 'get_settings',
  title: 'Get settings',
  description: 'Synced settings: global check-in thresholds (warningDays, criticalDays), birthday reminder offsets (days before: 0 = on the day), the hour of the daily reminder sweep and the time zone used for all dates. Defaults are filled in when the phone has not written them yet.',
  input: {},
  readOnly: true,
  handler: async (_args, ctx) => ({ ...(await loadSettings(ctx)), allowedBirthdayOffsets: ALLOWED_BIRTHDAY_OFFSETS }),
});

export const updateSettings = defineTool({
  name: 'update_settings',
  title: 'Update settings',
  description: `Changes the given synced settings. Thresholds are coerced like the app (warning >= 1, critical > warning). birthdayOffsets is the full list, values among ${ALLOWED_BIRTHDAY_OFFSETS.join(', ')}. timeZone is an IANA id (e.g. Europe/Rome).`,
  input: {
    warningDays: z.number().int().min(1).max(3650).optional(),
    criticalDays: z.number().int().min(2).max(3650).optional(),
    birthdayOffsets: z.array(z.number().int()).max(5).optional(),
    dailyReminderHour: z.number().int().min(0).max(23).optional(),
    timeZone: z.string().max(64).optional(),
  },
  idempotent: true,
  handler: async (args, ctx) => {
    const current = await loadSettings(ctx);
    const patch: Record<string, unknown> = {};
    if (args.warningDays !== undefined || args.criticalDays !== undefined) {
      Object.assign(patch, coerceGlobalThreshold(args.warningDays ?? current.warningDays, args.criticalDays ?? current.criticalDays));
    }
    if (args.birthdayOffsets !== undefined) {
      const bad = args.birthdayOffsets.filter((o) => !ALLOWED_BIRTHDAY_OFFSETS.includes(o));
      if (bad.length) throw new ToolError(`birthdayOffsets: allowed values are ${ALLOWED_BIRTHDAY_OFFSETS.join(', ')}`);
      patch.birthdayOffsets = [...new Set(args.birthdayOffsets)].sort((a, b) => a - b);
    }
    if (args.dailyReminderHour !== undefined) patch.dailyReminderHour = args.dailyReminderHour;
    if (args.timeZone !== undefined) {
      if (!isValidTimeZone(args.timeZone)) throw new ToolError(`Unknown time zone: ${args.timeZone}`);
      patch.timeZone = args.timeZone;
    }
    if (Object.keys(patch).length === 0) throw new ToolError('Nothing to update: pass at least one field');
    await ctx.store.batch().update('settings', 'main', patch).commit();
    ctx.store.invalidate();
    return { updated: true, settings: await loadSettings(ctx) };
  },
});
