import { describe, expect, it } from 'vitest';
import { assertDocId, isValidDocId, parseUserPath } from '../src/domain/paths.js';
import { decodeSpecial } from '../src/tools/raw.js';

describe('parseUserPath', () => {
  it('riconosce documenti e collezioni dal numero di segmenti', () => {
    expect(parseUserPath('transactions/abc')).toMatchObject({ kind: 'document', relative: 'transactions/abc' });
    expect(parseUserPath('transactions')).toMatchObject({ kind: 'collection' });
    expect(parseUserPath('fin_accounts/xyz/')).toMatchObject({ kind: 'document', relative: 'fin_accounts/xyz' });
    expect(parseUserPath('a/b/c')).toMatchObject({ kind: 'collection', segments: ['a', 'b', 'c'] });
  });

  it.each([
    ['', 'vuoto'],
    ['/transactions/abc', 'slash iniziale'],
    ['../other/transactions/x', '..'],
    ['transactions/../../x', '..'],
    ['transactions/./x', '.'],
    ['transactions//x', 'segmento vuoto'],
    ['users/other-uid/transactions/x', 'users'],
    ['users', 'users'],
    ['transactions/__name__', 'riservat'],
    ['tra\\nsactions/x', 'caratteri'],
    ['transactions/a\u0000b', 'caratteri'],
    ['a/b/c/d/e/f/g/h/i/j/k/l/m/n', 'profondo'],
  ])('rifiuta %j', (path, fragment) => {
    expect(() => parseUserPath(path)).toThrow(new RegExp(fragment, 'i'));
  });

  it('non e possibile uscire dal proprio uid con nessuna combinazione di segmenti', () => {
    const attempts = ['..', '../..', 'x/../..', '%2e%2e/x', '....//x', 'users/abc'];
    for (const attempt of attempts) {
      let relative: string | null = null;
      try {
        relative = parseUserPath(attempt).relative;
      } catch {
        continue; // rifiutato: va bene
      }
      // Se e' stato accettato non deve poter risalire: nessun segmento "." o "..".
      expect(relative!.split('/').some((s) => s === '..' || s === '.' || s === 'users')).toBe(false);
    }
  });
});

describe('decodeSpecial', () => {
  it('lascia invariati i valori normali e decodifica i marcatori', () => {
    expect(decodeSpecial({ a: 1, b: [1, 'x'], c: { d: null } })).toEqual({ a: 1, b: [1, 'x'], c: { d: null } });
    const ts = decodeSpecial({ $timestamp: '2026-10-17T10:00:00Z' }) as { toDate(): Date };
    expect(ts.toDate().toISOString()).toBe('2026-10-17T10:00:00.000Z');
    expect(() => decodeSpecial({ $timestamp: 'non-una-data' })).toThrow();
  });
});

describe('assertDocId', () => {
  it('accetta id normali', () => {
    for (const ok of ['abc123', 'def_cat_casa_spesa', 'sys_unknown_account', 'a.b', '2026-10-17', 'x'.repeat(100)]) expect(isValidDocId(ok), ok).toBe(true);
  });
  it.each(['', '/', 'a/b', '../x', '..', '.', '__name__', '__x__', 'a\u0000b', 'x'.repeat(1501)])('rifiuta %j', (bad) => {
    expect(isValidDocId(bad)).toBe(false);
    expect(() => assertDocId(bad)).toThrow(/non valido/);
  });
  it('rifiuta i tipi non stringa', () => {
    for (const bad of [null, undefined, 5, {}, ['a']]) expect(isValidDocId(bad)).toBe(false);
  });
});
