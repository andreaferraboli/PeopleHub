/**
 * Percorsi Firestore del tool generico: sempre RELATIVI a `users/{uid}/` e
 * validati, cosi' non si puo' uscire dal proprio utente.
 */
import { ToolError } from './errors.js';

export interface ParsedPath {
  /** Segmenti relativi (senza `users/{uid}`). */
  segments: string[];
  kind: 'document' | 'collection';
  /** Percorso relativo normalizzato. */
  relative: string;
}

const MAX_SEGMENTS = 12;

export function parseUserPath(raw: string): ParsedPath {
  if (typeof raw !== 'string') throw new ToolError('path: serve una stringa');
  const trimmed = raw.trim();
  if (!trimmed) throw new ToolError('path vuoto');
  if (trimmed.startsWith('/')) throw new ToolError('path: deve essere relativo a users/{uid}/, senza slash iniziale');
  if (trimmed.includes('\\') || [...trimmed].some((ch) => ch.charCodeAt(0) < 32 || ch.charCodeAt(0) === 127)) throw new ToolError('path: caratteri non ammessi');
  const segments = trimmed.replace(/\/+$/, '').split('/');
  if (segments.length > MAX_SEGMENTS) throw new ToolError('path: troppo profondo');
  for (const segment of segments) {
    if (segment === '') throw new ToolError('path: segmento vuoto');
    if (segment === '.' || segment === '..') throw new ToolError('path: "." e ".." non ammessi');
    if (/^__.*__$/.test(segment)) throw new ToolError('path: segmenti riservati (__nome__) non ammessi');
    if (Buffer.byteLength(segment) > 1500) throw new ToolError('path: segmento troppo lungo');
  }
  if (segments[0] === 'users') {
    throw new ToolError('path: e\' relativo a users/{uid}/ (non iniziare con "users/"): per es. "people/<uuid>" o "settings/main"');
  }
  return { segments, kind: segments.length % 2 === 0 ? 'document' : 'collection', relative: segments.join('/') };
}

/** True se `value` e' utilizzabile come id di documento Firestore: niente '/', '.', '..', '__x__'. */
export function isValidDocId(value: unknown): value is string {
  return (
    typeof value === 'string' &&
    value.length > 0 &&
    Buffer.byteLength(value) <= 1500 &&
    !value.includes('/') &&
    value !== '.' &&
    value !== '..' &&
    !/^__.*__$/.test(value) &&
    ![...value].some((ch) => ch.charCodeAt(0) < 32 || ch.charCodeAt(0) === 127)
  );
}

/** Come isValidDocId, ma lancia: va usato su OGNI id che arriva da fuori prima di costruire un percorso. */
export function assertDocId(value: unknown, label = 'id'): string {
  if (!isValidDocId(value)) throw new ToolError(`${label} non valido (niente "/", ".", "..", "__x__")`);
  return value;
}
