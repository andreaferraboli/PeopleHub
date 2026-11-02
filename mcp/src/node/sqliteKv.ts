/**
 * KV persistente su SQLite (node:sqlite) con la superficie di Workers KV che usa la libreria OAuth:
 * get, getWithMetadata, put (expiration / expirationTtl, metadata), delete, list (prefix, limit, cursor).
 * Sulla VPS sostituisce Workers KV: un file in /var/lib/peoplehub-mcp, letto e scritto solo dal servizio.
 */
import type { DatabaseSync as DatabaseSyncT, StatementSync } from 'node:sqlite';

// Caricato a runtime: Vite (test) non conosce ancora "node:sqlite" come modulo interno.
const { DatabaseSync } = process.getBuiltinModule('node:sqlite') as typeof import('node:sqlite');

type GetType = 'text' | 'json';
type GetOptions = GetType | { type?: GetType | string; cacheTtl?: number } | undefined;

interface Row {
  value: string;
  expires_at: number | null;
  metadata: string | null;
}

const typeOf = (options: GetOptions) => (typeof options === 'string' ? options : options?.type) ?? 'text';
const decode = (value: string, type: string) => (type === 'json' ? JSON.parse(value) : value);

export class SqliteKV {
  private readonly db: DatabaseSyncT;
  private readonly sGet: StatementSync;
  private readonly sPut: StatementSync;
  private readonly sDelete: StatementSync;
  private readonly sList: StatementSync;
  private readonly sPurge: StatementSync;
  private readonly timer?: ReturnType<typeof setInterval>;

  /** `nowSec` e' iniettabile per i test. `purgeEveryMs` = 0 spegne la pulizia periodica. */
  constructor(file: string, private readonly nowSec: () => number = () => Date.now() / 1000, purgeEveryMs = 10 * 60_000) {
    this.db = new DatabaseSync(file);
    this.db.exec(`
      PRAGMA journal_mode = WAL;
      PRAGMA synchronous = NORMAL;
      PRAGMA busy_timeout = 5000;
      CREATE TABLE IF NOT EXISTS kv (key TEXT PRIMARY KEY, value TEXT NOT NULL, expires_at REAL, metadata TEXT) WITHOUT ROWID;
      CREATE INDEX IF NOT EXISTS kv_expires ON kv (expires_at) WHERE expires_at IS NOT NULL;
    `);
    this.sGet = this.db.prepare('SELECT value, expires_at, metadata FROM kv WHERE key = ?');
    this.sPut = this.db.prepare('INSERT INTO kv (key, value, expires_at, metadata) VALUES (?, ?, ?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value, expires_at = excluded.expires_at, metadata = excluded.metadata');
    this.sDelete = this.db.prepare('DELETE FROM kv WHERE key = ?');
    this.sList = this.db.prepare('SELECT key, expires_at, metadata FROM kv WHERE key > ? AND substr(key, 1, length(?)) = ? AND (expires_at IS NULL OR expires_at > ?) ORDER BY key LIMIT ?');
    this.sPurge = this.db.prepare('DELETE FROM kv WHERE expires_at IS NOT NULL AND expires_at <= ?');
    if (purgeEveryMs > 0) {
      this.timer = setInterval(() => this.purgeExpired(), purgeEveryMs);
      this.timer.unref();
    }
  }

  private live(key: string): Row | undefined {
    const row = this.sGet.get(key) as Row | undefined;
    if (!row) return undefined;
    if (row.expires_at !== null && row.expires_at <= this.nowSec()) {
      this.sDelete.run(key);
      return undefined;
    }
    return row;
  }

  async get(key: string, options?: GetOptions): Promise<unknown> {
    const row = this.live(key);
    return row ? decode(row.value, typeOf(options)) : null;
  }

  async getWithMetadata(key: string, options?: GetOptions): Promise<{ value: unknown; metadata: unknown }> {
    const row = this.live(key);
    if (!row) return { value: null, metadata: null };
    return { value: decode(row.value, typeOf(options)), metadata: row.metadata === null ? null : JSON.parse(row.metadata) };
  }

  async put(key: string, value: unknown, options?: { expiration?: number; expirationTtl?: number; metadata?: unknown }): Promise<void> {
    const text = typeof value === 'string' ? value : value instanceof ArrayBuffer ? new TextDecoder().decode(value) : String(value);
    const expiresAt = options?.expiration ?? (options?.expirationTtl ? this.nowSec() + options.expirationTtl : null);
    const metadata = options?.metadata === undefined ? null : JSON.stringify(options.metadata);
    this.sPut.run(key, text, expiresAt, metadata);
  }

  async delete(key: string): Promise<void> {
    this.sDelete.run(key);
  }

  async list(options?: { prefix?: string | null; limit?: number; cursor?: string | null }): Promise<{
    keys: Array<{ name: string; expiration?: number; metadata?: unknown }>;
    list_complete: boolean;
    cursor?: string;
  }> {
    const prefix = options?.prefix ?? '';
    const limit = Math.max(1, Math.min(options?.limit ?? 1000, 1000));
    // il cursore e' l'ultima chiave restituita: si riparte dalla successiva
    const after = options?.cursor ?? '';
    const rows = this.sList.all(after, prefix, prefix, this.nowSec(), limit + 1) as Array<{ key: string; expires_at: number | null; metadata: string | null }>;
    const page = rows.slice(0, limit);
    const complete = rows.length <= limit;
    return {
      keys: page.map((r) => ({
        name: r.key,
        ...(r.expires_at !== null ? { expiration: Math.floor(r.expires_at) } : {}),
        ...(r.metadata !== null ? { metadata: JSON.parse(r.metadata) } : {}),
      })),
      list_complete: complete,
      ...(complete ? {} : { cursor: page[page.length - 1].key }),
    };
  }

  /** Cancella le chiavi scadute (le letture le ignorano comunque). Restituisce quante. */
  purgeExpired(): number {
    return Number(this.sPurge.run(this.nowSec()).changes);
  }

  close(): void {
    if (this.timer) clearInterval(this.timer);
    this.db.close();
  }
}
