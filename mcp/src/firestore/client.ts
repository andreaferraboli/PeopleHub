/**
 * Client Firestore via REST (nessun SDK: gira nei Cloudflare Workers) con la
 * stessa forma d'uso dell'Admin SDK per il sottoinsieme che serve ai tool:
 * collection/doc, get/set/update/create/delete, where/orderBy/limit/startAfter,
 * count, batch.
 *
 * Ogni percorso passa da `allow`: il Worker apre solo `users/{uid}/...` dell'utente
 * del token e le quattro collezioni dei conti condivisi, mai altro.
 */
import {
  DocumentReferenceValue,
  FieldValue,
  decodeFields,
  decodeValue,
  encodeFields,
  encodeValue,
  fieldPath,
  type DocumentData,
  type EncodeContext,
  type RestValue,
} from './values.js';

export { DocumentReferenceValue, FieldValue, Timestamp } from './values.js';
export type { DocumentData } from './values.js';

export type TokenProvider = () => Promise<string>;

export interface FirestoreOptions {
  projectId: string;
  databaseId?: string;
  /** Token OAuth2 (service account) per l'API; con l'emulatore basta una stringa fissa. */
  getToken: TokenProvider;
  /** Origine dell'API: https://firestore.googleapis.com, oppure http://127.0.0.1:8080 per l'emulatore. */
  baseUrl?: string;
  /** Quali percorsi (segmenti) si possono toccare. Vuoto = nessun limite (solo test). */
  allow?: (segments: string[]) => boolean;
  fetchImpl?: typeof fetch;
}

export class FirestoreError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string
  ) {
    super(message);
    this.name = 'FirestoreError';
  }
}

type FilterOp = '==' | '!=' | '<' | '<=' | '>' | '>=' | 'array-contains' | 'in' | 'array-contains-any' | 'not-in';
const OPS: Record<FilterOp, string> = {
  '==': 'EQUAL',
  '!=': 'NOT_EQUAL',
  '<': 'LESS_THAN',
  '<=': 'LESS_THAN_OR_EQUAL',
  '>': 'GREATER_THAN',
  '>=': 'GREATER_THAN_OR_EQUAL',
  'array-contains': 'ARRAY_CONTAINS',
  in: 'IN',
  'array-contains-any': 'ARRAY_CONTAINS_ANY',
  'not-in': 'NOT_IN',
};

interface RestDocument {
  name: string;
  fields?: Record<string, RestValue>;
  createTime?: string;
  updateTime?: string;
}

interface RestWrite {
  update?: { name: string; fields: Record<string, RestValue> };
  updateMask?: { fieldPaths: string[] };
  updateTransforms?: Array<Record<string, unknown>>;
  delete?: string;
  currentDocument?: { exists: boolean };
}

const AUTO_ID_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';

function autoId(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(20));
  return Array.from(bytes, (b) => AUTO_ID_CHARS[b % AUTO_ID_CHARS.length]).join('');
}

export class Firestore {
  readonly databaseName: string;
  private readonly baseUrl: string;
  private readonly fetchImpl: typeof fetch;
  readonly encodeCtx: EncodeContext;

  constructor(private readonly options: FirestoreOptions) {
    this.databaseName = `projects/${options.projectId}/databases/${options.databaseId ?? '(default)'}`;
    this.baseUrl = (options.baseUrl ?? 'https://firestore.googleapis.com').replace(/\/+$/, '');
    this.fetchImpl = options.fetchImpl ?? ((input, init) => fetch(input, init));
    this.encodeCtx = { databaseName: this.databaseName };
  }

  collection(path: string): CollectionReference {
    return new CollectionReference(this, splitPath(path));
  }

  doc(path: string): DocumentReference {
    return new DocumentReference(this, splitPath(path));
  }

  batch(): WriteBatch {
    return new WriteBatch(this);
  }

  /** Documento dalla risposta REST (nome completo -> percorso relativo). */
  snapshotOf(doc: RestDocument): QueryDocumentSnapshot {
    const path = doc.name.slice(`${this.databaseName}/documents/`.length);
    return new QueryDocumentSnapshot(new DocumentReference(this, splitPath(path)), true, decodeFields(doc.fields));
  }

  assertAllowed(segments: string[]): void {
    if (this.options.allow && !this.options.allow(segments)) throw new FirestoreError(`Percorso non consentito: ${segments.join('/')}`, 403, 'path_not_allowed');
  }

  /** Nome completo (per il corpo delle richieste). */
  documentName(segments: string[]): string {
    return `${this.databaseName}/documents/${segments.join('/')}`;
  }

  /** Percorso per l'URL (segmenti codificati). */
  urlPath(segments: string[]): string {
    return segments.map(encodeURIComponent).join('/');
  }

  /** URL dell'API per un percorso di documenti (o per la radice). */
  url(suffix: string): string {
    return `${this.baseUrl}/v1/${this.databaseName}/documents${suffix}`;
  }

  async request<T>(method: string, url: string, body?: unknown, allowNotFound = false): Promise<T | null> {
    let lastError: FirestoreError | undefined;
    for (let attempt = 0; attempt < 3; attempt++) {
      const token = await this.options.getToken();
      const res = await this.fetchImpl(url, {
        method,
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      if (res.ok) return (await res.json()) as T;
      const text = await res.text();
      if (res.status === 404 && allowNotFound) return null;
      let code: string | undefined;
      let message = text.slice(0, 300);
      try {
        const parsed = JSON.parse(text) as { error?: { status?: string; message?: string } };
        code = parsed.error?.status;
        message = parsed.error?.message ?? message;
      } catch {
        // risposta non JSON
      }
      lastError = new FirestoreError(`Firestore ${res.status} ${code ?? ''}: ${message}`, res.status, code);
      if (res.status !== 429 && res.status < 500) break;
      await new Promise((r) => setTimeout(r, 150 * (attempt + 1)));
    }
    throw lastError ?? new FirestoreError('Firestore: errore sconosciuto', 500);
  }
}

function splitPath(path: string): string[] {
  const segments = path.split('/').filter((s) => s.length > 0);
  if (segments.length === 0) throw new FirestoreError('Percorso vuoto', 400);
  return segments;
}

export class DocumentSnapshot {
  constructor(
    readonly ref: DocumentReference,
    readonly exists: boolean,
    private readonly _data: DocumentData
  ) {}
  get id(): string {
    return this.ref.id;
  }
  data(): DocumentData | undefined {
    return this.exists ? this._data : undefined;
  }
  get(field: string): unknown {
    let value: unknown = this._data;
    for (const part of field.split('.')) {
      if (value && typeof value === 'object') value = (value as Record<string, unknown>)[part];
      else return undefined;
    }
    return value;
  }
}

/** Documento restituito da una query: esiste sempre, quindi `data()` non e' mai undefined. */
export class QueryDocumentSnapshot extends DocumentSnapshot {
  override data(): DocumentData {
    return super.data() as DocumentData;
  }
}

export class QuerySnapshot {
  constructor(readonly docs: QueryDocumentSnapshot[]) {}
  get size(): number {
    return this.docs.length;
  }
  get empty(): boolean {
    return this.docs.length === 0;
  }
}

export class DocumentReference {
  constructor(
    readonly db: Firestore,
    readonly segments: string[]
  ) {
    if (segments.length % 2 !== 0) throw new FirestoreError(`Non e' un documento: ${segments.join('/')}`, 400);
  }
  get id(): string {
    return this.segments[this.segments.length - 1];
  }
  get path(): string {
    return this.segments.join('/');
  }
  collection(name: string): CollectionReference {
    return new CollectionReference(this.db, [...this.segments, ...splitPath(name)]);
  }

  async get(): Promise<DocumentSnapshot> {
    this.db.assertAllowed(this.segments);
    const doc = await this.db.request<RestDocument>('GET', this.db.url(`/${this.db.urlPath(this.segments)}`), undefined, true);
    return doc ? this.db.snapshotOf(doc) : new DocumentSnapshot(this, false, {});
  }

  async set(data: DocumentData, options?: { merge?: boolean }): Promise<void> {
    await new WriteBatch(this.db).set(this, data, options).commit();
  }
  async update(data: DocumentData): Promise<void> {
    await new WriteBatch(this.db).update(this, data).commit();
  }
  async create(data: DocumentData): Promise<void> {
    await new WriteBatch(this.db).create(this, data).commit();
  }
  async delete(): Promise<void> {
    await new WriteBatch(this.db).delete(this).commit();
  }
}

interface Filter {
  field: string;
  op: FilterOp;
  value: unknown;
}
interface Order {
  field: string;
  direction: 'asc' | 'desc';
}

export class Query {
  constructor(
    readonly db: Firestore,
    /** Percorso della collezione (numero dispari di segmenti). */
    readonly segments: string[],
    protected readonly filters: Filter[] = [],
    protected readonly orders: Order[] = [],
    protected readonly limitTo?: number,
    protected readonly cursor?: DocumentSnapshot
  ) {}

  protected clone(patch: { filters?: Filter[]; orders?: Order[]; limitTo?: number; cursor?: DocumentSnapshot }): Query {
    return new Query(this.db, this.segments, patch.filters ?? this.filters, patch.orders ?? this.orders, patch.limitTo ?? this.limitTo, patch.cursor ?? this.cursor);
  }

  where(field: string, op: FilterOp, value: unknown): Query {
    return this.clone({ filters: [...this.filters, { field, op, value }] });
  }
  orderBy(field: string, direction: 'asc' | 'desc' = 'asc'): Query {
    return this.clone({ orders: [...this.orders, { field, direction }] });
  }
  limit(n: number): Query {
    return this.clone({ limitTo: n });
  }
  startAfter(snapshot: DocumentSnapshot): Query {
    return this.clone({ cursor: snapshot });
  }

  private structured(): Record<string, unknown> {
    const q: Record<string, unknown> = { from: [{ collectionId: this.segments[this.segments.length - 1] }] };
    const fieldFilters = this.filters.map((f) => ({
      fieldFilter: { field: { fieldPath: fieldPath(f.field.split('.')) }, op: OPS[f.op], value: encodeValue(f.value, this.db.encodeCtx) },
    }));
    if (fieldFilters.length === 1) q.where = fieldFilters[0];
    else if (fieldFilters.length > 1) q.where = { compositeFilter: { op: 'AND', filters: fieldFilters } };
    // Con un cursore l'ordine per id documento va esplicito (stessa direzione dell'ultimo campo), come l'Admin SDK.
    const orders: Order[] = this.cursor ? [...this.orders, { field: '__name__', direction: this.orders[this.orders.length - 1]?.direction ?? 'asc' }] : this.orders;
    if (orders.length > 0) q.orderBy = orders.map((o) => ({ field: { fieldPath: o.field === '__name__' ? '__name__' : fieldPath(o.field.split('.')) }, direction: o.direction === 'desc' ? 'DESCENDING' : 'ASCENDING' }));
    if (this.limitTo !== undefined) q.limit = this.limitTo;
    if (this.cursor) {
      const values = this.orders.map((o) => encodeValue(this.cursor!.get(o.field), this.db.encodeCtx));
      values.push({ referenceValue: `${this.db.databaseName}/documents/${this.cursor.ref.path}` });
      q.startAt = { values, before: false };
    }
    return q;
  }

  private parentUrl(): string {
    const parent = this.segments.slice(0, -1);
    return this.db.url(parent.length ? `/${parent.map(encodeURIComponent).join('/')}:` : ':');
  }

  async get(): Promise<QuerySnapshot> {
    this.db.assertAllowed(this.segments);
    const rows = (await this.db.request<Array<{ document?: RestDocument }>>('POST', `${this.parentUrl()}runQuery`, { structuredQuery: this.structured() })) ?? [];
    return new QuerySnapshot(rows.filter((r) => r.document).map((r) => this.db.snapshotOf(r.document as RestDocument)));
  }

  /** Aggregazione count(): una lettura ogni 1000 documenti che corrispondono. */
  count(): { get(): Promise<{ data(): { count: number } }> } {
    return {
      get: async () => {
        this.db.assertAllowed(this.segments);
        const structuredQuery = this.structured();
        const rows =
          (await this.db.request<Array<{ result?: { aggregateFields?: { count?: RestValue } } }>>('POST', `${this.parentUrl()}runAggregationQuery`, {
            structuredAggregationQuery: { structuredQuery, aggregations: [{ alias: 'count', count: {} }] },
          })) ?? [];
        const raw = rows.find((r) => r.result)?.result?.aggregateFields?.count;
        const count = raw ? Number(decodeValue(raw)) : 0;
        return { data: () => ({ count }) };
      },
    };
  }
}

export class CollectionReference extends Query {
  constructor(db: Firestore, segments: string[]) {
    super(db, segments);
    if (segments.length % 2 !== 1) throw new FirestoreError(`Non e' una collezione: ${segments.join('/')}`, 400);
  }
  get id(): string {
    return this.segments[this.segments.length - 1];
  }
  get path(): string {
    return this.segments.join('/');
  }
  /** Con id: riferimento a quel documento; senza: id automatico (20 caratteri, come Firestore). */
  doc(id?: string): DocumentReference {
    const parts = id === undefined ? [autoId()] : splitPath(id);
    return new DocumentReference(this.db, [...this.segments, ...parts]);
  }
}

type Op = { kind: 'set'; ref: DocumentReference; data: DocumentData; merge: boolean } | { kind: 'update'; ref: DocumentReference; data: DocumentData } | { kind: 'create'; ref: DocumentReference; data: DocumentData } | { kind: 'delete'; ref: DocumentReference };

export class WriteBatch {
  private ops: Op[] = [];
  constructor(private readonly db: Firestore) {}

  set(ref: DocumentReference, data: DocumentData, options?: { merge?: boolean }): this {
    this.ops.push({ kind: 'set', ref, data, merge: options?.merge === true });
    return this;
  }
  update(ref: DocumentReference, data: DocumentData): this {
    this.ops.push({ kind: 'update', ref, data });
    return this;
  }
  create(ref: DocumentReference, data: DocumentData): this {
    this.ops.push({ kind: 'create', ref, data });
    return this;
  }
  delete(ref: DocumentReference): this {
    this.ops.push({ kind: 'delete', ref });
    return this;
  }

  async commit(): Promise<void> {
    if (this.ops.length === 0) return;
    const writes = this.ops.map((op) => this.toWrite(op));
    for (let i = 0; i < writes.length; i += 400) {
      await this.db.request('POST', `${this.db.url(':commit')}`, { writes: writes.slice(i, i + 400) });
    }
  }

  private toWrite(op: Op): RestWrite {
    this.db.assertAllowed(op.ref.segments);
    const name = this.db.documentName(op.ref.segments);
    if (op.kind === 'delete') return { delete: name };
    const ctx = this.db.encodeCtx;
    const transforms: Array<Record<string, unknown>> = [];
    const deletes: string[] = [];
    const plain: DocumentData = {};
    const maskPaths: string[] = [];

    // Separa i valori speciali dai valori normali, a qualunque profondita' (solo mappe).
    const walk = (value: unknown, path: string[], target: DocumentData | null, key: string): void => {
      if (value instanceof FieldValue) {
        const p = fieldPath(path);
        if (value.kind === 'delete') deletes.push(p);
        else if (value.kind === 'serverTimestamp') transforms.push({ fieldPath: p, setToServerValue: 'REQUEST_TIME' });
        else if (value.kind === 'arrayUnion') transforms.push({ fieldPath: p, appendMissingElements: { values: (value.operand as unknown[]).map((v) => encodeValue(v, ctx)) } });
        else if (value.kind === 'arrayRemove') transforms.push({ fieldPath: p, removeAllFromArray: { values: (value.operand as unknown[]).map((v) => encodeValue(v, ctx)) } });
        else transforms.push({ fieldPath: p, increment: encodeValue(value.operand, ctx) });
        return;
      }
      if (value === undefined) return;
      const isPlainMap = value !== null && typeof value === 'object' && !Array.isArray(value) && Object.getPrototypeOf(value) === Object.prototype;
      if (isPlainMap && Object.keys(value as object).length > 0 && (op.kind === 'update' || (op.kind === 'set' && op.merge))) {
        // mappa non vuota: si scende, cosi' la maschera tocca solo le foglie (merge profondo)
        const child: DocumentData = {};
        if (target) target[key] = child;
        for (const [k, v] of Object.entries(value as DocumentData)) walk(v, [...path, k], child, k);
        return;
      }
      if (target) target[key] = value;
      maskPaths.push(fieldPath(path));
    };

    if (op.kind === 'update') {
      // update(): le chiavi con il punto sono percorsi annidati.
      const root: DocumentData = {};
      for (const [k, v] of Object.entries(op.data)) {
        const parts = k.split('.');
        let target = root;
        for (const part of parts.slice(0, -1)) {
          target[part] = (target[part] as DocumentData) ?? {};
          target = target[part] as DocumentData;
        }
        walk(v, parts, target, parts[parts.length - 1]);
      }
      Object.assign(plain, root);
    } else {
      for (const [k, v] of Object.entries(op.data)) walk(v, [k], plain, k);
    }

    const write: RestWrite = { update: { name, fields: encodeFields(plain, ctx) } };
    if (op.kind === 'update') {
      write.updateMask = { fieldPaths: [...maskPaths, ...deletes] };
      write.currentDocument = { exists: true };
    } else if (op.kind === 'set' && op.merge) {
      write.updateMask = { fieldPaths: [...maskPaths, ...deletes] };
    } else {
      if (deletes.length > 0) throw new FirestoreError('FieldValue.delete() non e valido in set() senza merge', 400);
      if (op.kind === 'create') write.currentDocument = { exists: false };
      // set() senza merge: sostituisce il documento; le trasformazioni si applicano dopo.
    }
    if (transforms.length > 0) write.updateTransforms = transforms;
    return write;
  }
}

export { DocumentReferenceValue as DocumentReferenceData };
