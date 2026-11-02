/**
 * Handler fetch del server: authorization server OAuth 2.1 (libreria @cloudflare/workers-oauth-provider,
 * stato nel KV) + endpoint /mcp. Sulla VPS lo serve src/node/main.ts.
 */
import OAuthProvider from '@cloudflare/workers-oauth-provider';
import { SCOPE_ACCESS, SCOPE_OFFLINE, type Env, type SessionProps } from './config.js';
import { handleMcp, type McpDeps } from './mcpHandler.js';
import { isAllowedRedirectUri } from './oauth/redirectUris.js';
import { allowLoopback, createWebHandler, type WebDeps } from './web.js';

export interface WorkerDeps extends WebDeps, McpDeps {}

const RATE_LIMITED_PATHS = new Set(['/register', '/authorize', '/token', '/oauth/login', '/admin/revoke', '/admin/mint']);

export function createWorker(deps: WorkerDeps = {}) {
  const providers = new Map<string, OAuthProvider<Env>>();
  // Stato della richiesta corrente, per le chiamate interne (callback di registrazione, /admin/mint).
  let currentEnv: Env;
  let currentCtx: ExecutionContext;
  let loopbackAllowed = false;

  function getProvider(env: Env, request: Request): OAuthProvider<Env> {
    const origin = (env.PUBLIC_URL || new URL(request.url).origin).replace(/\/+$/, '');
    const key = `${origin}|${allowLoopback(env)}`;
    let provider = providers.get(key);
    if (provider) return provider;
    const web = createWebHandler({
      ...deps,
      tokenEndpointFetch: (req) => (providers.get(key) as OAuthProvider<Env>).fetch(req, currentEnv, currentCtx),
    });
    provider = new OAuthProvider<Env>({
      apiRoute: '/mcp',
      apiHandler: { fetch: (request, env, ctx) => handleMcp(request, env, (ctx as unknown as { props?: Partial<SessionProps> }).props, deps) },
      defaultHandler: { fetch: (request, env) => web(request, env) },
      authorizeEndpoint: '/authorize',
      tokenEndpoint: '/token',
      clientRegistrationEndpoint: '/register',
      scopesSupported: [SCOPE_ACCESS, SCOPE_OFFLINE],
      requiredScopes: [SCOPE_ACCESS],
      resourceMetadata: { resource: `${origin}/mcp`, authorization_servers: [origin], resource_name: 'PeopleHub' },
      accessTokenTTL: 60 * 60,
      refreshTokenTTL: 30 * 24 * 60 * 60,
      // Registrazione dinamica: solo i callback di Claude (e i loopback in sviluppo).
      clientRegistrationCallback: ({ clientMetadata }) => {
        const uris = Array.isArray(clientMetadata.redirect_uris) ? (clientMetadata.redirect_uris as unknown[]).map(String) : [];
        if (uris.length === 0 || !uris.every((u) => isAllowedRedirectUri(u, { allowLoopback: loopbackAllowed }))) {
          return { code: 'invalid_redirect_uri', description: 'redirect_uri non ammesso', status: 400 };
        }
      },
    });
    providers.set(key, provider);
    return provider;
  }

  return {
    async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
      currentEnv = env;
      currentCtx = ctx;
      loopbackAllowed = allowLoopback(env);
      const path = new URL(request.url).pathname;
      if (env.RATE_LIMIT && RATE_LIMITED_PATHS.has(path)) {
        const key = request.headers.get('CF-Connecting-IP') ?? 'unknown';
        const { success } = await env.RATE_LIMIT.limit({ key: `${path}|${key}` });
        if (!success) return new Response('Too Many Requests', { status: 429, headers: { 'Retry-After': '60' } });
      }
      // OAuth 2.1: allo scambio del codice redirect_uri e' obbligatorio (la libreria lo controlla solo se presente).
      if (path === '/token' && request.method === 'POST') {
        const form = await request.clone().formData().catch(() => null);
        if (form && form.get('grant_type') === 'authorization_code' && !form.get('redirect_uri')) {
          return new Response(JSON.stringify({ error: 'invalid_request', error_description: 'redirect_uri obbligatorio' }), { status: 400, headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' } });
        }
      }
      return getProvider(env, request).fetch(request, env, ctx);
    },
  };
}
