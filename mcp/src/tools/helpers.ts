/**
 * Infrastruttura comune dei tool: contesto, registrazione, risposte, schemi.
 */
import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import type { Firestore } from '../firestore/index.js';
import { DEFAULT_TIME_ZONE, formatLocalDate, isValidTimeZone, localDateOf, type LocalDate } from '../domain/dates.js';
import { ToolError } from '../domain/errors.js';
import { isValidDocId } from '../domain/paths.js';
import { mathRandomInt, type RandomInt } from '../domain/reminders.js';
import { PeopleStore, type ReadCache } from '../store.js';

export interface ToolContext {
  uid: string;
  email: string;
  db: Firestore;
  store: PeopleStore;
  /** Orologio iniettabile (test). */
  now: () => Date;
  /** Caso iniettabile (jitter dei promemoria). */
  random: RandomInt;
  /** UUID dei documenti nuovi (iniettabile). */
  newId: () => string;
}

export function makeContext(input: {
  uid: string;
  email: string;
  db: Firestore;
  cacheTtlMs?: number;
  cache?: ReadCache;
  now?: () => Date;
  random?: RandomInt;
  newId?: () => string;
}): ToolContext {
  const now = input.now ?? (() => new Date());
  return {
    uid: input.uid,
    email: input.email,
    db: input.db,
    store: new PeopleStore(input.db, input.uid, input.cacheTtlMs ?? 0, input.cache, () => now().getTime()),
    now,
    random: input.random ?? mathRandomInt,
    newId: input.newId ?? (() => crypto.randomUUID()),
  };
}

/** Fuso dell'utente: settings/main.timeZone, default Europe/Rome. */
export async function userTimeZone(ctx: ToolContext): Promise<string> {
  const tz = (await ctx.store.settings()).timeZone;
  return isValidTimeZone(tz) ? tz : DEFAULT_TIME_ZONE;
}

export interface Clock {
  tz: string;
  nowMs: number;
  today: LocalDate;
  todayIso: string;
}

export async function clock(ctx: ToolContext): Promise<Clock> {
  const tz = await userTimeZone(ctx);
  const nowMs = ctx.now().getTime();
  const today = localDateOf(nowMs, tz);
  return { tz, nowMs, today, todayIso: formatLocalDate(today) };
}

/* ------------------------------------------------------------- registrazione */

export interface ToolDef<S extends z.ZodRawShape> {
  name: string;
  title: string;
  description: string;
  input: S;
  /** Solo lettura: non modifica nulla. */
  readOnly?: boolean;
  /** Puo' cancellare (tombstone) o sovrascrivere dati esistenti. */
  destructive?: boolean;
  idempotent?: boolean;
  handler: (args: z.infer<z.ZodObject<S>>, ctx: ToolContext) => Promise<unknown>;
}

const MAX_RESPONSE_CHARS = 120_000;

export type ToolRegistrar = ((server: McpServer, ctx: ToolContext) => void) & { readonly def: AnyToolDef };
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type AnyToolDef = ToolDef<any>;

export type ToolOutcome = { ok: true; result: unknown } | { ok: false; error: string };

/** Esegue un tool con argomenti gia validati: svuota la cache dopo le scritture e traduce gli errori. */
export async function runTool(def: AnyToolDef, args: unknown, ctx: ToolContext): Promise<ToolOutcome> {
  try {
    const result = await def.handler(args as never, ctx);
    if (def.readOnly !== true) ctx.store.invalidate();
    return { ok: true, result: result ?? { ok: true } };
  } catch (error) {
    ctx.store.invalidate();
    return { ok: false, error: describeError(error) };
  }
}

export function defineTool<S extends z.ZodRawShape>(def: ToolDef<S>): ToolRegistrar {
  const register = (server: McpServer, ctx: ToolContext) => {
    server.registerTool(
      def.name,
      {
        title: def.title,
        description: def.description,
        inputSchema: def.input,
        annotations: {
          title: def.title,
          readOnlyHint: def.readOnly === true,
          destructiveHint: def.readOnly === true ? false : def.destructive === true,
          idempotentHint: def.readOnly === true ? true : def.idempotent === true,
          openWorldHint: false,
        },
      },
      (async (args: z.infer<z.ZodObject<S>>) => {
        const outcome = await runTool(def as AnyToolDef, args, ctx);
        if (!outcome.ok) return errorResult(outcome.error);
        const text = JSON.stringify(outcome.result);
        if (text.length > MAX_RESPONSE_CHARS) {
          return errorResult(`Response too large (${text.length} chars): narrow the filters or use limit/offset.`);
        }
        return { content: [{ type: 'text' as const, text }] };
      }) as never
    );
  };
  return Object.assign(register, { def: def as AnyToolDef });
}

function errorResult(message: string) {
  return { isError: true, content: [{ type: 'text' as const, text: message }] };
}

export function describeError(error: unknown): string {
  if (error instanceof ToolError) return error.message;
  const code = (error as { code?: unknown }).code;
  const message = error instanceof Error ? error.message : 'unknown error';
  console.error('[tool] errore', code ?? '', message.slice(0, 300));
  return `Error: ${message.slice(0, 300)}`;
}

/* ---------------------------------------------------------------- schemi comuni */

/** Id di documento: validato per non poter costruire percorsi arbitrari. */
export const docIdSchema = z.string().min(1).max(200).refine(isValidDocId, 'invalid id (no "/", ".", "..", "__x__")');

export const dayString = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Date in the format YYYY-MM-DD');
export const timeString = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'Time in the format HH:mm (24h)');
/** Istante ISO 8601 con scarto esplicito (Z o +hh:mm): senza scarto l'ora sarebbe ambigua. */
export const instantString = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?([zZ]|[+-]\d{2}:\d{2})$/, 'ISO 8601 instant with offset, e.g. 2026-10-03T18:30:00+02:00');

/** Istante ISO -> epoch millis (lo schema ha gia controllato la forma). */
export function parseInstant(text: string, label = 'at'): number {
  const ms = Date.parse(text);
  if (Number.isNaN(ms)) throw new ToolError(`${label}: invalid instant ${text}`);
  return ms;
}

/** "HH:mm" -> [ore, minuti]. */
export function hourMinute(time: string): [number, number] {
  const [h, m] = time.split(':').map(Number);
  return [h, m];
}

/** Ora del giorno "HH:mm" di un istante nel fuso. */
export function timeOfDay(ms: number, tz: string): string {
  return new Intl.DateTimeFormat('en-GB', { timeZone: tz, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(ms));
}

export const pagination = {
  limit: z.number().int().min(1).max(500).default(100).describe('How many rows to return (max 500).'),
  offset: z.number().int().min(0).default(0).describe('Row to start from (pagination).'),
};

export function page<T>(items: T[], limit: number, offset: number) {
  const slice = items.slice(offset, offset + limit);
  return { total: items.length, offset, returned: slice.length, hasMore: offset + slice.length < items.length, items: slice };
}

/** Testo ripulito: trim, vuoto -> null. */
export function cleanText(value: string | null | undefined): string | null {
  const t = value?.trim();
  return t ? t : null;
}
