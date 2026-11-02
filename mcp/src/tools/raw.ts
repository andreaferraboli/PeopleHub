/**
 * Lettura di riserva di qualunque documento o collezione sotto users/{uid}/ (sola lettura).
 * Il percorso e' sempre relativo all'utente autenticato e validato (domain/paths.ts).
 */
import { z } from 'zod';
import { Timestamp, type Query } from '../firestore/index.js';
import { ToolError } from '../domain/errors.js';
import { assertDocId, parseUserPath } from '../domain/paths.js';
import { toPlain } from '../store.js';
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
