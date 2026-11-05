/**
 * Lettura e scrittura di riserva di qualunque documento sotto users/{uid}/.
 * Il percorso e' sempre relativo all'utente autenticato e validato (domain/paths.ts); il client Firestore rifiuta
 * comunque ogni percorso fuori da users/{uid}/. Le scritture portano i campi di sync del contratto e non cancellano
 * mai davvero: delete = tombstone.
 */
import { z } from 'zod';
import { FieldValue, Timestamp, type DocumentData, type Query } from '../firestore/index.js';
import { ToolError } from '../domain/errors.js';
import { assertDocId, parseUserPath } from '../domain/paths.js';
import { toPlain, UPDATED_BY } from '../store.js';
import { defineTool, docIdSchema } from './helpers.js';

/** Valori dei filtri: {"$timestamp":"ISO"} diventa un Timestamp, il resto resta com'e'. */
export function decodeSpecial(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(decodeSpecial);
  if (value && typeof value === 'object') {
    const obj = value as Record<string, unknown>;
    const keys = Object.keys(obj);
    if (keys.length === 1 && keys[0] === '$timestamp') {
      const d = new Date(String(obj.$timestamp));
      if (Number.isNaN(d.getTime())) throw new ToolError('$timestamp non valido');
      return Timestamp.fromDate(d);
    }
    return Object.fromEntries(keys.map((key) => [key, decodeSpecial(obj[key])]));
  }
  return value;
}

export const rawRead = defineTool({
  name: 'raw_read',
  title: 'Raw read (document or collection)',
  description:
    'Fallback read of a document or collection under users/{uid}/ given the RELATIVE path (e.g. "people/<id>", "settings/main", "checkIns"). Collections support where (max 5), orderBy, limit and a startAfter cursor. Returns raw documents, tombstones (deleted: true) included unless includeDeleted is false. Prefer the specific tools.',
  input: {
    path: z.string().min(1).max(500).describe('Path relative to users/{uid}/. Even number of segments = document, odd = collection.'),
    where: z
      .array(z.object({ field: z.string().min(1), op: z.enum(['==', '!=', '<', '<=', '>', '>=', 'array-contains', 'in', 'array-contains-any', 'not-in']), value: z.unknown() }))
      .max(5)
      .optional(),
    orderBy: z.object({ field: z.string().min(1), direction: z.enum(['asc', 'desc']).default('asc') }).optional(),
    limit: z.number().int().min(1).max(500).default(50),
    startAfter: docIdSchema.optional().describe('Document id to resume after (pagination).'),
    includeDeleted: z.boolean().default(true),
  },
  readOnly: true,
  handler: async (args, ctx) => {
    const parsed = parseUserPath(args.path);
    const base = `users/${ctx.uid}/${parsed.relative}`;
    if (parsed.kind === 'document') {
      const snap = await ctx.db.doc(base).get();
      return { path: parsed.relative, exists: snap.exists, data: snap.exists ? toPlain(snap.data()) : null };
    }
    let query: Query = ctx.db.collection(base);
    for (const w of args.where ?? []) query = query.where(w.field, w.op, decodeSpecial(w.value));
    if (args.orderBy) query = query.orderBy(args.orderBy.field, args.orderBy.direction);
    if (args.startAfter) {
      const cursor = await ctx.db.doc(`${base}/${assertDocId(args.startAfter, 'startAfter')}`).get();
      if (cursor.exists) query = query.startAfter(cursor);
    }
    const snap = await query.limit(args.limit).get();
    const docs = snap.docs.filter((d) => args.includeDeleted || d.data().deleted !== true);
    return {
      path: parsed.relative,
      count: docs.length,
      nextCursor: snap.size === args.limit ? snap.docs[snap.size - 1].id : null,
      documents: docs.map((d) => ({ id: d.id, data: toPlain(d.data()) })),
    };
  },
});

/** Campi di sync scritti dal server: i dati in ingresso non li possono impostare (tranne `deleted`). */
const SYNC_FIELDS = new Set(['updatedAt', 'serverUpdatedAt', 'updatedBy']);

/**
 * Valori dei dati di raw_write: {"$timestamp":"ISO"} -> Timestamp, {"$serverTimestamp":true} -> ora del server,
 * {"$delete":true} -> cancella il campo (solo merge). Il resto resta com'e'.
 */
export function decodeWriteValue(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(decodeWriteValue);
  if (value && typeof value === 'object') {
    const obj = value as Record<string, unknown>;
    const keys = Object.keys(obj);
    if (keys.length === 1 && keys[0] === '$serverTimestamp' && obj.$serverTimestamp === true) return FieldValue.serverTimestamp();
    if (keys.length === 1 && keys[0] === '$delete' && obj.$delete === true) return FieldValue.delete();
    if (keys.length === 1 && keys[0] === '$timestamp') return decodeSpecial(obj);
    return Object.fromEntries(keys.map((key) => [key, decodeWriteValue(obj[key])]));
  }
  return value;
}

function hasFieldDelete(value: unknown): boolean {
  if (value instanceof FieldValue) return value.kind === 'delete';
  if (Array.isArray(value)) return value.some(hasFieldDelete);
  if (value && typeof value === 'object' && !(value instanceof Timestamp)) return Object.values(value).some(hasFieldDelete);
  return false;
}

export const rawWrite = defineTool({
  name: 'raw_write',
  title: 'Raw write (one document)',
  description:
    'ONLY ON EXPLICIT USER REQUEST, for what no specific tool covers. Writes ONE document under users/{uid}/ given its RELATIVE path (even number of segments, e.g. "reminders/<id>", "settings/main"). op: create (new document, fails if it exists), set (replaces the whole document), merge (merges the given fields, nested maps included), delete (tombstone: deleted=true, the document is kept; never a hard delete). Every write stamps the sync fields (updatedAt, serverUpdatedAt, updatedBy "mcp") and deleted=false unless data sets deleted (merge {deleted:false} restores a tombstone). Special values in data: {"$timestamp":"ISO"}, {"$serverTimestamp":true}, {"$delete":true} (merge only: removes the field). Applies NO app rule: no lastCheckInAt / nextFireAt recompute, no cascades, no validation of the schema in docs/firestore-schema.md; prefer the specific tools.',
  input: {
    op: z.enum(['create', 'set', 'merge', 'delete']),
    path: z.string().min(1).max(500).describe('Path of a DOCUMENT relative to users/{uid}/.'),
    data: z.record(z.unknown()).optional().describe('Fields to write (not for delete).'),
  },
  destructive: true,
  handler: async ({ op, path, data }, ctx) => {
    const parsed = parseUserPath(path);
    if (parsed.kind !== 'document') throw new ToolError('path must point to a document (even number of segments)');
    const ref = ctx.db.doc(`users/${ctx.uid}/${parsed.relative}`);
    const sync = { updatedAt: ctx.now().getTime(), serverUpdatedAt: FieldValue.serverTimestamp(), updatedBy: UPDATED_BY };
    const batch = ctx.db.batch();
    if (op === 'delete') {
      if (data && Object.keys(data).length > 0) throw new ToolError('delete takes no data');
      const snap = await ref.get();
      if (!snap.exists) throw new ToolError(`Document not found: ${parsed.relative}`);
      batch.set(ref, { ...sync, deleted: true }, { merge: true });
      await batch.commit();
      return { ok: true, op, path: parsed.relative, tombstone: true };
    }
    if (!data || Object.keys(data).length === 0) throw new ToolError('data is required');
    const blocked = Object.keys(data).filter((k) => SYNC_FIELDS.has(k));
    if (blocked.length) throw new ToolError(`Sync fields are written by the server: remove ${blocked.join(', ')}`);
    const decoded = decodeWriteValue(data) as DocumentData;
    if (op !== 'merge' && hasFieldDelete(decoded)) throw new ToolError('{"$delete":true} works only with op merge');
    if (decoded.deleted !== undefined && typeof decoded.deleted !== 'boolean') throw new ToolError('deleted must be a boolean');
    const doc = { ...decoded, ...sync, deleted: decoded.deleted ?? false };
    if (op === 'create') batch.create(ref, doc);
    else batch.set(ref, doc, { merge: op === 'merge' });
    await batch.commit();
    return { ok: true, op, path: parsed.relative, deleted: doc.deleted };
  },
});
