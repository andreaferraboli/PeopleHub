/**
 * Rotte non protette del Worker: pagina /authorize (accesso con Google + consenso),
 * POST /oauth/login, lo script della pagina e gli endpoint /admin (revoca, coniazione di verifica).
 */
import { AuthorizationError } from '@cloudflare/workers-oauth-provider';
import { verifyFirebaseIdToken, type IdTokenVerifier } from './auth/firebaseToken.js';
import { checkIdentityAtLogin } from './auth/session.js';
import { SCOPE_ACCESS, isEmailAllowed, parseAllowlist, type Env, type SessionProps } from './config.js';
import { LOGIN_JS } from './generated/loginJs.js';
import { buildLoginCsp, renderLoginPage } from './oauth/loginPage.js';
import { isAllowedRedirectUri } from './oauth/redirectUris.js';

const CLAUDE_CALLBACK = 'https://claude.ai/api/mcp/auth_callback';
const MINT_CLIENT_KEY = 'ph:mint-client';
/** Indice email -> uid nel KV (`ph:uid:<email>`), per revocare i token di un utente dall'email. */
const UID_INDEX_PREFIX = 'ph:uid:';

export interface WebDeps {
  verifyIdToken?: IdTokenVerifier;
  now?: () => number;
  /** Per /admin/mint: scambia il codice sul token endpoint del provider. */
  tokenEndpointFetch?: (request: Request) => Promise<Response>;
}

export const allowLoopback = (env: Env) => env.ALLOW_LOOPBACK === 'true';

/** Nome del client come lo mostra la pagina: lo sceglie chi registra il client, quindi va ripulito e limitato. */
export function displayClientName(name: string | undefined): string {
  const clean = [...(name ?? '')].filter((ch) => ch.charCodeAt(0) >= 32 && ch.charCodeAt(0) !== 127).join('').trim().slice(0, 60);
  return clean || 'senza nome';
}

const text = (status: number, body: string, headers: Record<string, string> = {}) =>
  new Response(body, { status, headers: { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store', ...headers } });

const json = (status: number, body: unknown, extra?: Headers) => {
  const res = new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' } });
  for (const c of extra?.getSetCookie() ?? []) res.headers.append('Set-Cookie', c);
  return res;
};

function constantTimeEqual(a: string, b: string): boolean {
  const ea = new TextEncoder().encode(a);
  const eb = new TextEncoder().encode(b);
  let diff = ea.length ^ eb.length;
  for (let i = 0; i < Math.max(ea.length, eb.length); i++) diff |= (ea[i] ?? 0) ^ (eb[i] ?? 0);
  return diff === 0;
}

function publicOrigin(env: Env, request: Request): string {
  return (env.PUBLIC_URL || new URL(request.url).origin).replace(/\/+$/, '');
}

export function createWebHandler(deps: WebDeps = {}) {
  const verify = deps.verifyIdToken ?? verifyFirebaseIdToken;
  const nowSec = () => Math.floor((deps.now ?? Date.now)() / 1000);

  async function authorizePage(request: Request, env: Env): Promise<Response> {
    const oauth = env.OAUTH_PROVIDER;
    try {
      const authRequest = await oauth.parseAuthRequest(request);
      if (!isAllowedRedirectUri(authRequest.redirectUri, { allowLoopback: allowLoopback(env) })) {
        return text(400, 'redirect_uri non ammesso');
      }
      const details = await oauth.describeConsent(authRequest);
      const consent = await oauth.beginConsent(authRequest);
      const nonce = btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(16))));
      const html = renderLoginPage({
        nonce,
        handle: consent.handle,
        clientName: displayClientName(details.clientName),
        redirectHost: details.redirectHost,
        redirectIsLoopback: details.redirectIsLoopback,
        authEmulatorHost: env.FIREBASE_AUTH_EMULATOR_HOST,
      });
      const headers = consent.headers;
      headers.set('Content-Type', 'text/html; charset=utf-8');
      headers.set('Cache-Control', 'no-store');
      headers.set('X-Frame-Options', 'DENY');
      headers.set('Content-Security-Policy', buildLoginCsp(env.FIREBASE_AUTH_EMULATOR_HOST, nonce));
      headers.set('Referrer-Policy', 'no-referrer');
      headers.set('X-Content-Type-Options', 'nosniff');
      return new Response(html, { status: 200, headers });
    } catch (error) {
      if (error instanceof AuthorizationError && error.redirectTo) {
        return Response.redirect(error.redirectTo, 302);
      }
      if (error instanceof AuthorizationError) return text(400, error.description ?? 'Richiesta non valida');
      throw error;
    }
  }

  async function login(request: Request, env: Env): Promise<Response> {
    if (request.method !== 'POST') return text(405, 'Method Not Allowed', { Allow: 'POST' });
    const origin = request.headers.get('Origin');
    if (origin && origin !== publicOrigin(env, request)) return json(403, { error: 'origin_not_allowed' });
    const body = (await request.json().catch(() => ({}))) as { handle?: unknown; idToken?: unknown; deny?: unknown };
    if (typeof body.handle !== 'string' || body.handle.length > 200) return json(400, { error: 'invalid_request' });
    const oauth = env.OAUTH_PROVIDER;

    try {
      if (body.deny === true) {
        const denied = await oauth.denyConsent(request, body.handle);
        return json(200, { redirectTo: denied.redirectTo }, denied.headers);
      }
      if (typeof body.idToken !== 'string') return json(400, { error: 'invalid_request' });
      let identity;
      try {
        identity = await verify(body.idToken, env);
      } catch {
        return json(401, { error: 'invalid_id_token', message: 'Accesso Google non valido, riprova.' });
      }
      const denial = checkIdentityAtLogin(identity, env, nowSec());
      if (denial) return json(denial.status, { error: denial.error, message: denial.message });

      const approved = await oauth.approveConsent(request, body.handle);
      if (!isAllowedRedirectUri(approved.request.redirectUri, { allowLoopback: allowLoopback(env) })) {
        return json(400, { error: 'invalid_request', message: 'redirect_uri non ammesso' });
      }
      const scope = [...new Set([SCOPE_ACCESS, ...approved.request.scope])];
      const props: SessionProps = { uid: identity.uid, email: identity.email, provider: identity.provider, authTime: identity.authTime };
      const { redirectTo } = await oauth.completeAuthorization({ request: approved.request, userId: identity.uid, metadata: { email: identity.email }, scope, props });
      // indice email -> uid, per revocare "tutti i token di un utente" dall'email
      await env.OAUTH_KV.put(`${UID_INDEX_PREFIX}${identity.email}`, identity.uid);
      return json(200, { redirectTo }, approved.headers);
    } catch (error) {
      if (error instanceof AuthorizationError) return json(400, { error: 'invalid_request', message: 'Richiesta scaduta o gia usata: riavvia il collegamento da Claude.' });
      throw error;
    }
  }

  function adminAuthorized(request: Request, env: Env): boolean {
    if (!env.ADMIN_TOKEN || env.ADMIN_TOKEN.length < 32) return false;
    const header = request.headers.get('Authorization') ?? '';
    return header.startsWith('Bearer ') && constantTimeEqual(header.slice(7), env.ADMIN_TOKEN);
  }

  /**
   * Email di "tutti" per la revoca: chi e' nell'allowlist (senza il jolly) piu chiunque abbia un indice
   * `ph:uid:<email>` nel KV (scritto a ogni login e a ogni conio), cosi con ALLOWED_EMAILS=* si revocano tutti gli utenti noti.
   */
  async function knownEmails(env: Env): Promise<string[]> {
    const emails = parseAllowlist(env.ALLOWED_EMAILS);
    emails.delete('*');
    let cursor: string | undefined;
    do {
      const page = await env.OAUTH_KV.list({ prefix: UID_INDEX_PREFIX, cursor, limit: 1000 });
      for (const key of page.keys) emails.add(key.name.slice(UID_INDEX_PREFIX.length));
      cursor = page.list_complete ? undefined : page.cursor;
    } while (cursor);
    return [...emails];
  }

  /**
   * POST /admin/revoke {email} | {uid} | {all: true} [+ mintOnly]: revoca i grant (e quindi i token) dell'utente;
   * all = tutti gli utenti noti; uid = quello dato (serve per i token coniati su un uid di test).
   */
  async function adminRevoke(request: Request, env: Env): Promise<Response> {
    const body = (await request.json().catch(() => ({}))) as { email?: string; uid?: string; all?: boolean; mintOnly?: boolean };
    const emails = body.all === true ? await knownEmails(env) : typeof body.email === 'string' ? [body.email.toLowerCase()] : [];
    const uids = typeof body.uid === 'string' && body.uid ? [body.uid] : [];
    if (emails.length === 0 && uids.length === 0) return json(400, { error: 'invalid_request', message: 'Passa {"email": "..."}, {"uid": "..."} oppure {"all": true} (opzionale mintOnly)' });
    for (const email of emails) {
      const uid = await env.OAUTH_KV.get(`${UID_INDEX_PREFIX}${email}`);
      if (uid) uids.push(uid);
    }
    let revoked = 0;
    for (const uid of new Set(uids)) {
      let cursor: string | undefined;
      do {
        const page = await env.OAUTH_PROVIDER.listUserGrants(uid, { cursor, limit: 100 });
        for (const grant of page.items) {
          // mintOnly: solo i grant coniati da /admin/mint (verifiche), mai i collegamenti veri di Claude
          if (body.mintOnly === true && !(grant.metadata as { mint?: boolean } | undefined)?.mint) continue;
          await env.OAUTH_PROVIDER.revokeGrant(grant.id, uid);
          revoked++;
        }
        cursor = page.cursor;
      } while (cursor);
    }
    return json(200, { revokedGrants: revoked });
  }

  /** POST /admin/mint {uid,email}: access token per verifiche (letture sui dati veri). Passa dal flusso OAuth vero. */
  async function adminMint(request: Request, env: Env): Promise<Response> {
    const body = (await request.json().catch(() => ({}))) as { uid?: string; email?: string };
    if (!body.uid || !body.email) return json(400, { error: 'invalid_request' });
    const email = body.email.toLowerCase();
    if (!isEmailAllowed(env.ALLOWED_EMAILS, email)) return json(403, { error: 'not_allowed' });
    const oauth = env.OAUTH_PROVIDER;
    const origin = publicOrigin(env, request);
    // Client "admin" creato una volta (la libreria assegna l'id): il suo id si ricorda in KV.
    let mintClientId = await env.OAUTH_KV.get(MINT_CLIENT_KEY);
    if (!mintClientId || !(await oauth.lookupClient(mintClientId))) {
      const created = await oauth.createClient({ clientName: 'PeopleHub admin', redirectUris: [CLAUDE_CALLBACK], tokenEndpointAuthMethod: 'none' });
      mintClientId = created.clientId;
      await env.OAUTH_KV.put(MINT_CLIENT_KEY, mintClientId);
    }
    const verifier = btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(32)))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const challenge = btoa(String.fromCharCode(...new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const props: SessionProps = { uid: body.uid, email, provider: 'google.com', authTime: nowSec() };
    const { redirectTo } = await oauth.completeAuthorization({
      request: { responseType: 'code', clientId: mintClientId, redirectUri: CLAUDE_CALLBACK, scope: [SCOPE_ACCESS], state: '', codeChallenge: challenge, codeChallengeMethod: 'S256', resource: `${origin}/mcp`, issuer: origin },
      userId: body.uid,
      metadata: { email, mint: true },
      scope: [SCOPE_ACCESS],
      props,
      revokeExistingGrants: false,
    });
    // niente indice email -> uid qui (punterebbe l'email all'uid di test): i token coniati si revocano con /admin/revoke {uid}
    const code = new URL(redirectTo).searchParams.get('code');
    if (!code || !deps.tokenEndpointFetch) return json(500, { error: 'server_error' });
    const res = await deps.tokenEndpointFetch(
      new Request(`${origin}/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ grant_type: 'authorization_code', code, client_id: mintClientId, code_verifier: verifier, redirect_uri: CLAUDE_CALLBACK, resource: `${origin}/mcp` }),
      })
    );
    return json(res.status, await res.json());
  }

  return async function fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    switch (url.pathname) {
      case '/authorize':
        return request.method === 'GET' ? authorizePage(request, env) : text(405, 'Method Not Allowed', { Allow: 'GET' });
      case '/oauth/login':
        return login(request, env);
      case '/mcp-assets/login.js':
        return new Response(LOGIN_JS, { headers: { 'Content-Type': 'text/javascript; charset=utf-8', 'Cache-Control': 'public, max-age=300', 'X-Content-Type-Options': 'nosniff' } });
      case '/admin/revoke':
      case '/admin/mint': {
        if (request.method !== 'POST' || !env.ADMIN_TOKEN) return text(404, 'Not Found');
        if (!adminAuthorized(request, env)) return text(401, 'Unauthorized', { 'WWW-Authenticate': 'Bearer' });
        return url.pathname === '/admin/revoke' ? adminRevoke(request, env) : adminMint(request, env);
      }
      case '/':
        return new Response('<!doctype html><meta charset="utf-8"><meta name="robots" content="noindex"><title>PeopleHub MCP</title><p style="font-family:system-ui;max-width:32rem;margin:4rem auto">Server MCP privato di PeopleHub.</p>', { headers: { 'Content-Type': 'text/html; charset=utf-8' } });
      default:
        return text(404, 'Not Found');
    }
  };
}
