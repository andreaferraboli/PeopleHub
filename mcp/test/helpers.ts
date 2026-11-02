import { randomUUID } from 'node:crypto';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { Firestore } from '../src/firestore/index.js';
import { allowedPaths } from '../src/data.js';
import { buildMcpServer } from '../src/tools/index.js';
import { makeContext, type ToolContext } from '../src/tools/helpers.js';

export const EMULATOR = process.env.FIRESTORE_EMULATOR_HOST;
export const TEST_PROJECT = 'demo-ph-mcp';

/** Firestore REST contro l'emulatore (progetto demo, mai dati veri). `uid` = confinato come in produzione. */
export function testDb(uid?: string): Firestore {
  if (!EMULATOR) throw new Error('FIRESTORE_EMULATOR_HOST non impostato');
  return new Firestore({ projectId: TEST_PROJECT, baseUrl: `http://${EMULATOR}`, getToken: async () => 'owner', ...(uid ? { allow: allowedPaths(uid) } : {}) });
}

export interface Harness {
  uid: string;
  db: Firestore;
  ctx: ToolContext;
  client: Client;
  call<T = any>(name: string, args?: Record<string, unknown>): Promise<T>; // eslint-disable-line @typescript-eslint/no-explicit-any
  callError(name: string, args?: Record<string, unknown>): Promise<string>;
  /** Documento grezzo users/{uid}/<path> (tombstone compresi). */
  doc(path: string): Promise<Record<string, unknown> | undefined>;
  close(): Promise<void>;
}

export async function makeHarness(options: { uid?: string; now?: () => Date; random?: (from: number, until: number) => number } = {}): Promise<Harness> {
  const uid = options.uid ?? `u_${randomUUID().slice(0, 12)}`;
  const db = testDb(uid);
  const ctx = makeContext({ uid, email: `${uid}@example.com`, db, now: options.now ?? (() => new Date('2026-11-02T10:00:00Z')), random: options.random ?? ((from) => from) });
  const server = buildMcpServer(ctx);
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: 'test', version: '1.0.0' });
  await Promise.all([server.connect(serverTransport), client.connect(clientTransport)]);
  const raw = async (name: string, args: Record<string, unknown> = {}) => {
    const result = (await client.callTool({ name, arguments: args })) as { isError?: boolean; content: Array<{ type: string; text: string }> };
    return { isError: result.isError === true, text: result.content[0]?.text ?? '' };
  };
  return {
    uid,
    db,
    ctx,
    client,
    async call(name, args) {
      const r = await raw(name, args);
      if (r.isError) throw new Error(`${name}: ${r.text}`);
      return JSON.parse(r.text);
    },
    async callError(name, args) {
      const r = await raw(name, args);
      if (!r.isError) throw new Error(`${name}: atteso un errore, ricevuto ${r.text.slice(0, 200)}`);
      return r.text;
    },
    async doc(path) {
      return (await db.doc(`users/${uid}/${path}`).get()).data();
    },
    async close() {
      await client.close();
      await server.close();
    },
  };
}
