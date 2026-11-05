/**
 * Adattatore Node della VPS: KV su SQLite, limite di richieste e server HTTP davanti al Worker.
 * Il server gira davvero (http.createServer su una porta locale casuale), il login Google e' simulato.
 */
import { createHash, randomBytes } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { createServer, request as httpRequest, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import type { VerifiedIdentity } from '../src/auth/firebaseToken.js';
import type { Env } from '../src/config.js';
import { createNodeHandler } from '../src/node/http.js';
import { MemoryRateLimiter } from '../src/node/rateLimit.js';
import { SqliteKV } from '../src/node/sqliteKv.js';
import { createWorker } from '../src/worker.js';

const PUBLIC = 'https://people-mcp.example.test';
const CALLBACK = 'https://claude.ai/api/mcp/auth_callback';
const ANDREA = 'andrew.ferro04@gmail.com';
const dir = mkdtempSync(path.join(tmpdir(), 'ph-mcp-node-'));
afterAll(() => rmSync(dir, { recursive: true, force: true }));

describe('SqliteKV', () => {
  let now = 1_000_000;
  const kv = new SqliteKV(path.join(dir, 'kv.sqlite'), () => now, 0);
  afterAll(() => kv.close());

  it('get/put/delete, json e metadata', async () => {
    await kv.put('a', JSON.stringify({ x: 1 }), { metadata: { m: true } });
    expect(await kv.get('a')).toBe('{"x":1}');
    expect(await kv.get('a', { type: 'json' })).toEqual({ x: 1 });
    expect(await kv.getWithMetadata('a', 'json')).toEqual({ value: { x: 1 }, metadata: { m: true } });
    await kv.put('a', 'v2');
    expect(await kv.getWithMetadata('a')).toEqual({ value: 'v2', metadata: null });
    await kv.delete('a');
    expect(await kv.get('a')).toBeNull();
  });

  it('scadenza con expirationTtl ed expiration', async () => {
    await kv.put('ttl', 'x', { expirationTtl: 60 });
    await kv.put('abs', 'y', { expiration: now + 10 });
    expect(await kv.get('ttl')).toBe('x');
    now += 11;
    expect(await kv.get('abs')).toBeNull();
    expect(await kv.get('ttl')).toBe('x');
    now += 60;
    expect(await kv.get('ttl')).toBeNull();
  });

  it('list con prefisso (anche con _ e %), limite e cursore; ignora le scadute', async () => {
    for (let i = 0; i < 5; i++) await kv.put(`g:u1_%:${i}`, String(i));
    await kv.put('g:u1_%:scaduta', 'z', { expiration: now - 1 });
    await kv.put('g:u1x:0', 'altro');
    await kv.put('h:0', 'fuori');
    const first = await kv.list({ prefix: 'g:u1_%:', limit: 3 });
    expect(first.keys.map((k) => k.name)).toEqual(['g:u1_%:0', 'g:u1_%:1', 'g:u1_%:2']);
    expect(first.list_complete).toBe(false);
    const second = await kv.list({ prefix: 'g:u1_%:', limit: 3, cursor: first.cursor });
    expect(second.keys.map((k) => k.name)).toEqual(['g:u1_%:3', 'g:u1_%:4']);
    expect(second.list_complete).toBe(true);
    expect(second.cursor).toBeUndefined();
    expect(kv.purgeExpired()).toBeGreaterThanOrEqual(1);
  });

  it('persiste su disco', async () => {
    const file = path.join(dir, 'persist.sqlite');
    const a = new SqliteKV(file, () => now, 0);
    await a.put('k', 'v');
    a.close();
    const b = new SqliteKV(file, () => now, 0);
    expect(await b.get('k')).toBe('v');
    b.close();
  });
});

describe('MemoryRateLimiter', () => {
  it('finestra fissa per chiave', async () => {
    let t = 0;
    const rl = new MemoryRateLimiter(2, 60, () => t);
    expect((await rl.limit({ key: 'a' })).success).toBe(true);
    expect((await rl.limit({ key: 'a' })).success).toBe(true);
    expect((await rl.limit({ key: 'a' })).success).toBe(false);
    expect((await rl.limit({ key: 'b' })).success).toBe(true);
    t += 60_000;
    expect((await rl.limit({ key: 'a' })).success).toBe(true);
    rl.close();
  });
});

describe('server HTTP Node davanti al Worker', () => {
  let server: Server;
  let base: string;
  let kv: SqliteKV;
  let rl: MemoryRateLimiter;
  const verifyIdToken = async (token: string): Promise<VerifiedIdentity> => {
    if (token === 'tok-other') return { uid: 'uid-other', email: 'someone@example.com', emailVerified: true, provider: 'google.com', authTime: Math.floor(Date.now() / 1000) };
    if (token !== 'tok-andrea') throw new Error('token sconosciuto');
    return { uid: 'uid-andrea', email: ANDREA, emailVerified: true, provider: 'google.com', authTime: Math.floor(Date.now() / 1000) };
  };

  beforeAll(async () => {
    kv = new SqliteKV(path.join(dir, 'server.sqlite'), undefined, 0);
    rl = new MemoryRateLimiter(1000, 60);
    const env = { OAUTH_KV: kv as unknown as KVNamespace, RATE_LIMIT: rl, PUBLIC_URL: PUBLIC, ALLOWED_EMAILS: ANDREA, FIREBASE_PROJECT_ID: 'peoplehub-af26' } as Omit<Env, 'OAUTH_PROVIDER'> as Env;
    const handler = createNodeHandler(createWorker({ verifyIdToken }), env, { publicUrl: PUBLIC, maxBodyBytes: 64 * 1024, onError: () => {} });
    server = createServer((req, res) => void handler(req, res));
    await new Promise<void>((r) => server.listen(0, '127.0.0.1', r));
    base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  });
  afterAll(async () => {
    await new Promise((r) => server.close(r));
    kv.close();
    rl.close();
  });

  /** Richiesta grezza (per inviare request-target arbitrari e leggere tutti i Set-Cookie). */
  const raw = (target: string, init: { method?: string; headers?: Record<string, string>; body?: string } = {}) =>
    new Promise<{ status: number; headers: Record<string, string | string[] | undefined>; body: string }>((resolve, reject) => {
      const url = new URL(base);
      const req = httpRequest({ host: url.hostname, port: url.port, path: target, method: init.method ?? 'GET', headers: init.headers }, (res) => {
        let body = '';
        res.setEncoding('utf8');
        res.on('data', (c) => (body += c));
        res.on('end', () => resolve({ status: res.statusCode ?? 0, headers: res.headers, body }));
      });
      req.on('error', reject);
      if (init.body) req.write(init.body);
      req.end();
    });

  it('discovery con l\'origine pubblica, non con quella locale', async () => {
    const as = JSON.parse((await raw('/.well-known/oauth-authorization-server')).body);
    expect(as.issuer).toBe(PUBLIC);
    expect(as.registration_endpoint).toBe(`${PUBLIC}/register`);
    const pr = await raw('/.well-known/oauth-protected-resource/mcp');
    expect(JSON.parse(pr.body).resource).toBe(`${PUBLIC}/mcp`);
  });

  it('POST /mcp senza token: 401 con WWW-Authenticate verso i metadati pubblici', async () => {
    const res = await raw('/mcp', { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}' });
    expect(res.status).toBe(401);
    expect(String(res.headers['www-authenticate'])).toContain(`resource_metadata="${PUBLIC}/.well-known/oauth-protected-resource/mcp"`);
    expect(res.headers['strict-transport-security']).toBeTruthy();
  });

  it('un request-target assoluto o "//host" non cambia l\'origine', async () => {
    const abs = await raw('http://evil.example/.well-known/oauth-authorization-server');
    expect(abs.status).toBe(400);
    const pr = await raw('//evil.example/.well-known/oauth-authorization-server');
    expect(pr.body).not.toContain('evil.example');
  });

  it('corpo oltre il limite: 413', async () => {
    const res = await raw('/register', { method: 'POST', headers: { 'content-type': 'application/json' }, body: 'x'.repeat(70 * 1024) });
    expect(res.status).toBe(413);
  });

  it('un account fuori allowlist non completa il login', async () => {
    const reg = await raw('/register', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ redirect_uris: [CALLBACK], client_name: 'Claude', token_endpoint_auth_method: 'none' }) });
    const clientId = JSON.parse(reg.body).client_id as string;
    const q = new URLSearchParams({ response_type: 'code', client_id: clientId, redirect_uri: CALLBACK, scope: 'people', state: 's', code_challenge: 'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM', code_challenge_method: 'S256', resource: `${PUBLIC}/mcp` });
    const page = await raw(`/authorize?${q}`);
    const cookies = ([] as string[]).concat(page.headers['set-cookie'] ?? []);
    const handle = /"handle":"([^"]+)"/.exec(page.body)?.[1];
    const login = await raw('/oauth/login', {
      method: 'POST',
      headers: { 'content-type': 'application/json', origin: PUBLIC, cookie: cookies.map((c) => c.split(';')[0]).join('; ') },
      body: JSON.stringify({ handle, idToken: 'tok-other' }),
    });
    expect(login.status).toBe(403);
  });

  it('flusso completo: DCR, /authorize (cookie), login, token, tools/list', async () => {
    const reg = await raw('/register', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ redirect_uris: [CALLBACK], client_name: 'Claude', token_endpoint_auth_method: 'none' }) });
    expect(reg.status).toBe(201);
    const clientId = JSON.parse(reg.body).client_id as string;
    const bad = await raw('/register', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ redirect_uris: ['https://evil.example/cb'], token_endpoint_auth_method: 'none' }) });
    expect(bad.status).toBe(400);

    const verifier = randomBytes(32).toString('base64url');
    const challenge = createHash('sha256').update(verifier).digest('base64url');
    const q = new URLSearchParams({ response_type: 'code', client_id: clientId, redirect_uri: CALLBACK, scope: 'people', state: 's1', code_challenge: challenge, code_challenge_method: 'S256', resource: `${PUBLIC}/mcp` });
    const page = await raw(`/authorize?${q}`);
    expect(page.status).toBe(200);
    expect(String(page.headers['content-security-policy'])).toContain("default-src 'none'");
    const cookies = ([] as string[]).concat(page.headers['set-cookie'] ?? []);
    expect(cookies.length).toBeGreaterThan(0);
    const handle = /"handle":"([^"]+)"/.exec(page.body)?.[1];
    expect(handle).toBeTruthy();

    const login = await raw('/oauth/login', {
      method: 'POST',
      headers: { 'content-type': 'application/json', origin: PUBLIC, cookie: cookies.map((c) => c.split(';')[0]).join('; ') },
      body: JSON.stringify({ handle, idToken: 'tok-andrea' }),
    });
    expect(login.status).toBe(200);
    const redirectTo = new URL(JSON.parse(login.body).redirectTo);
    expect(redirectTo.origin + redirectTo.pathname).toBe(CALLBACK);
    const code = redirectTo.searchParams.get('code')!;

    const tok = await raw('/token', {
      method: 'POST',
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ grant_type: 'authorization_code', code, client_id: clientId, code_verifier: verifier, redirect_uri: CALLBACK, resource: `${PUBLIC}/mcp` }).toString(),
    });
    expect(tok.status).toBe(200);
    const accessToken = JSON.parse(tok.body).access_token as string;

    const list = await raw('/mcp', {
      method: 'POST',
      headers: { 'content-type': 'application/json', accept: 'application/json, text/event-stream', authorization: `Bearer ${accessToken}` },
      body: JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'tools/list', params: {} }),
    });
    expect(list.status).toBe(200);
    const tools = JSON.parse(list.body).result.tools as Array<{ name: string; annotations: { readOnlyHint: boolean; destructiveHint: boolean } }>;
    expect(tools.length).toBe(33);
    expect(tools.filter((t) => t.annotations.readOnlyHint).map((t) => t.name).sort()).toEqual(
      [
        'get_person',
        'get_profile',
        'get_settings',
        'list_check_ins',
        'list_events',
        'list_outings',
        'list_overdue_people',
        'list_people',
        'list_reminder_completions',
        'list_reminders',
        'list_tags',
        'raw_read',
        'upcoming_birthdays',
      ].sort()
    );
    expect(tools.filter((t) => t.annotations.destructiveHint).map((t) => t.name).sort()).toEqual(
      ['delete_check_in', 'delete_event', 'delete_outing', 'delete_person', 'delete_reminder', 'delete_reminder_completion', 'raw_write', 'update_outing'].sort()
    );
  });
});
