/**
 * Dati di PeopleHub in Firestore, secondo il contratto docs/firestore-schema.md:
 * tutto sotto users/{uid}/, id documento = UUID, campi di sync su ogni scrittura
 * (updatedAt millis, serverUpdatedAt = REQUEST_TIME, deleted, updatedBy "mcp"), tombstone al posto delle cancellazioni.
 * Le letture escludono i documenti con deleted == true.
 */
import { FieldValue, Timestamp, type DocumentData, type Firestore } from './firestore/index.js';

export const COLLECTIONS = ['people', 'checkIns', 'events', 'reminders', 'reminderCompletions'] as const;
export type CollectionName = (typeof COLLECTIONS)[number];

export const UPDATED_BY = 'mcp';

export type Doc = { id: string } & DocumentData;

/** Cache di lettura del processo: chiave `uid/collezione`, tutti i documenti (anche i tombstone). */
export type ReadCache = Map<string, { at: number; docs: Doc[] }>;

/** Valori Firestore -> JSON (Timestamp in ISO). */
export function toPlain(value: unknown): unknown {
  if (value instanceof Timestamp) return value.toDate().toISOString();
  if (Array.isArray(value)) return value.map(toPlain);
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value as Record<string, unknown>).map(([k, v]) => [k, toPlain(v)]));
  return value;
}

export const isLive = (d: DocumentData | undefined): boolean => !!d && d.deleted !== true;

export class PeopleStore {
  private readonly local = new Map<string, Promise<Doc[]>>();

  constructor(
    private readonly db: Firestore,
    readonly uid: string,
    private readonly ttlMs = 0,
    private readonly cache: ReadCache = new Map(),
    private readonly nowMs: () => number = Date.now
  ) {}

  private path(coll: string): string {
    return `users/${this.uid}/${coll}`;
  }

  /** Tutti i documenti della collezione, tombstone compresi (memo per richiesta + cache di processo). */
  private async allRaw(coll: CollectionName): Promise<Doc[]> {
    const key = `${this.uid}/${coll}`;
    const hit = this.cache.get(key);
    if (this.ttlMs > 0 && hit && Date.now() - hit.at < this.ttlMs) return hit.docs;
    let p = this.local.get(coll);
    if (!p) {
      p = this.db
        .collection(this.path(coll))
        .get()
        .then((snap) => {
          const docs = snap.docs.map((d) => ({ ...d.data(), id: d.id }) as Doc);
          if (this.ttlMs > 0) this.cache.set(key, { at: Date.now(), docs });
          return docs;
        });
      this.local.set(coll, p);
    }
    return p;
  }

  /** Documenti vivi (deleted != true). */
  async all(coll: CollectionName): Promise<Doc[]> {
    return (await this.allRaw(coll)).filter(isLive);
  }

  /** Documento vivo per id, oppure null. */
  async get(coll: CollectionName, id: string): Promise<Doc | null> {
    return (await this.all(coll)).find((d) => d.id === id) ?? null;
  }

  /** settings/main (vuoto se manca o e' un tombstone). */
  async settings(): Promise<DocumentData> {
    const p = this.local.get('settings') as Promise<Doc[]> | undefined;
    const docs =
      (await p) ??
      (await (async () => {
        const snap = await this.db.doc(`${this.path('settings')}/main`).get();
        const out = snap.exists && isLive(snap.data()) ? [{ ...snap.data(), id: 'main' } as Doc] : [];
        this.local.set('settings', Promise.resolve(out));
        return out;
      })());
    return docs[0] ?? {};
  }

  /** Svuota memo e cache (dopo ogni scrittura). */
  invalidate(): void {
    this.local.clear();
    for (const c of [...COLLECTIONS, 'settings']) this.cache.delete(`${this.uid}/${c}`);
  }

  /** Lotto di scritture con i campi di sync applicati a ogni documento. */
  batch(): SyncBatch {
    return new SyncBatch(this.db, (c) => this.path(c), this.nowMs);
  }
}

export class SyncBatch {
  private readonly inner: ReturnType<Firestore['batch']>;
  private count = 0;

  constructor(
    private readonly db: Firestore,
    private readonly path: (coll: string) => string,
    private readonly nowMs: () => number
  ) {
    this.inner = db.batch();
  }

  private sync(): DocumentData {
    return { updatedAt: this.nowMs(), serverUpdatedAt: FieldValue.serverTimestamp(), updatedBy: UPDATED_BY };
  }

  /** Documento nuovo (fallisce se l'id esiste gia). */
  create(coll: CollectionName, id: string, data: DocumentData): this {
    this.inner.create(this.db.doc(`${this.path(coll)}/${id}`), { ...data, ...this.sync(), deleted: false });
    this.count++;
    return this;
  }

  /** Aggiorna i campi dati (merge): il resto del documento resta com'e'. */
  update(coll: CollectionName | 'settings', id: string, data: DocumentData): this {
    this.inner.set(this.db.doc(`${this.path(coll)}/${id}`), { ...data, ...this.sync(), deleted: false }, { merge: true });
    this.count++;
    return this;
  }

  /** Tombstone: deleted = true, il documento resta. */
  tombstone(coll: CollectionName, id: string): this {
    this.inner.set(this.db.doc(`${this.path(coll)}/${id}`), { ...this.sync(), deleted: true }, { merge: true });
    this.count++;
    return this;
  }

  get size(): number {
    return this.count;
  }

  async commit(): Promise<void> {
    await this.inner.commit();
  }
}
