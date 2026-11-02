/**
 * Valori Firestore REST <-> JavaScript, con le stesse classi dell'Admin SDK
 * che usa il resto del codice (Timestamp, FieldValue, DocumentReference).
 */

export type DocumentData = Record<string, unknown>;

export class Timestamp {
  constructor(
    readonly seconds: number,
    readonly nanoseconds: number
  ) {}
  static now(): Timestamp {
    return Timestamp.fromMillis(Date.now());
  }
  static fromMillis(ms: number): Timestamp {
    const seconds = Math.floor(ms / 1000);
    return new Timestamp(seconds, Math.round((ms - seconds * 1000) * 1e6));
  }
  static fromDate(date: Date): Timestamp {
    return Timestamp.fromMillis(date.getTime());
  }
  /** Da stringa RFC 3339 (anche con nanosecondi). */
  static fromRfc3339(text: string): Timestamp {
    const m = text.match(/^(.*?)(?:\.(\d{1,9}))?Z$/);
    const base = Date.parse(`${m ? m[1] : text}Z`);
    const nanos = m && m[2] ? Number(m[2].padEnd(9, '0')) : 0;
    return new Timestamp(Math.floor(base / 1000), nanos);
  }
  toMillis(): number {
    return this.seconds * 1000 + Math.floor(this.nanoseconds / 1e6);
  }
  toDate(): Date {
    return new Date(this.toMillis());
  }
  toRfc3339(): string {
    const iso = new Date(this.seconds * 1000).toISOString().slice(0, 19);
    return `${iso}.${String(this.nanoseconds).padStart(9, '0')}Z`;
  }
}

type FieldValueKind = 'delete' | 'serverTimestamp' | 'arrayUnion' | 'arrayRemove' | 'increment';

/** Valori speciali di scrittura (come `FieldValue` dell'Admin SDK). */
export class FieldValue {
  private constructor(
    readonly kind: FieldValueKind,
    readonly operand?: unknown
  ) {}
  static delete(): FieldValue {
    return new FieldValue('delete');
  }
  static serverTimestamp(): FieldValue {
    return new FieldValue('serverTimestamp');
  }
  static arrayUnion(...items: unknown[]): FieldValue {
    return new FieldValue('arrayUnion', items);
  }
  static arrayRemove(...items: unknown[]): FieldValue {
    return new FieldValue('arrayRemove', items);
  }
  static increment(n: number): FieldValue {
    return new FieldValue('increment', n);
  }
}

/** Riferimento minimo a un documento nei valori decodificati (solo il percorso). */
export class DocumentReferenceValue {
  constructor(readonly path: string) {}
  get id(): string {
    return this.path.split('/').pop() as string;
  }
}

export interface RestValue {
  nullValue?: null;
  booleanValue?: boolean;
  integerValue?: string;
  doubleValue?: number | string;
  timestampValue?: string;
  stringValue?: string;
  bytesValue?: string;
  referenceValue?: string;
  geoPointValue?: { latitude: number; longitude: number };
  arrayValue?: { values?: RestValue[] };
  mapValue?: { fields?: Record<string, RestValue> };
}

export interface EncodeContext {
  /** Nome completo del database: projects/P/databases/D (per i riferimenti). */
  databaseName: string;
}

export function encodeValue(value: unknown, ctx: EncodeContext): RestValue {
  if (value === null || value === undefined) return { nullValue: null };
  switch (typeof value) {
    case 'boolean':
      return { booleanValue: value };
    case 'number':
      if (Number.isInteger(value) && Number.isSafeInteger(value)) return { integerValue: String(value) };
      if (Number.isNaN(value)) return { doubleValue: 'NaN' };
      if (value === Infinity) return { doubleValue: 'Infinity' };
      if (value === -Infinity) return { doubleValue: '-Infinity' };
      return { doubleValue: value };
    case 'string':
      return { stringValue: value };
    case 'object': {
      if (value instanceof Timestamp) return { timestampValue: value.toRfc3339() };
      if (value instanceof Date) return { timestampValue: Timestamp.fromDate(value).toRfc3339() };
      if (value instanceof DocumentReferenceValue) return { referenceValue: `${ctx.databaseName}/documents/${value.path}` };
      if (value instanceof FieldValue) throw new Error('FieldValue non ammesso dentro un valore annidato');
      if (Array.isArray(value)) return { arrayValue: { values: value.map((v) => encodeValue(v, ctx)) } };
      return { mapValue: { fields: encodeFields(value as DocumentData, ctx) } };
    }
    default:
      throw new Error(`Tipo non supportato: ${typeof value}`);
  }
}

export function encodeFields(data: DocumentData, ctx: EncodeContext): Record<string, RestValue> {
  const out: Record<string, RestValue> = {};
  for (const [k, v] of Object.entries(data)) {
    if (v === undefined) continue;
    out[k] = encodeValue(v, ctx);
  }
  return out;
}

export function decodeValue(value: RestValue): unknown {
  if ('nullValue' in value) return null;
  if (value.booleanValue !== undefined) return value.booleanValue;
  if (value.integerValue !== undefined) return Number(value.integerValue);
  if (value.doubleValue !== undefined) return typeof value.doubleValue === 'string' ? Number(value.doubleValue) : value.doubleValue;
  if (value.timestampValue !== undefined) return Timestamp.fromRfc3339(value.timestampValue);
  if (value.stringValue !== undefined) return value.stringValue;
  if (value.bytesValue !== undefined) return value.bytesValue;
  if (value.referenceValue !== undefined) return new DocumentReferenceValue(value.referenceValue.replace(/^.*?\/documents\//, ''));
  if (value.geoPointValue !== undefined) return { latitude: value.geoPointValue.latitude, longitude: value.geoPointValue.longitude };
  if (value.arrayValue !== undefined) return (value.arrayValue.values ?? []).map(decodeValue);
  if (value.mapValue !== undefined) return decodeFields(value.mapValue.fields ?? {});
  return null;
}

export function decodeFields(fields: Record<string, RestValue> | undefined): DocumentData {
  const out: DocumentData = {};
  for (const [k, v] of Object.entries(fields ?? {})) out[k] = decodeValue(v);
  return out;
}

/** Segmento di percorso di campo, con apici inversi se non e' un identificatore semplice. */
export function quoteFieldSegment(segment: string): string {
  return /^[A-Za-z_][A-Za-z_0-9]*$/.test(segment) ? segment : `\`${segment.replace(/[\\`]/g, '\\$&')}\``;
}

export function fieldPath(segments: string[]): string {
  return segments.map(quoteFieldSegment).join('.');
}
