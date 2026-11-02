/**
 * Endpoint /mcp: Streamable HTTP senza stato dietro il token OAuth.
 * La libreria OAuth ha gia validato il token (audience = risorsa); qui si ricontrolla l'identita'
 * salvata nel grant (allowlist corrente, provider, eta' della sessione) a OGNI richiesta.
 */
import { WebStandardStreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/webStandardStreamableHttp.js';
import { checkSession } from './auth/session.js';
import type { Env, SessionProps } from './config.js';
import { makeFirestore } from './data.js';
import type { ReadCache } from './store.js';
import { buildMcpServer } from './tools/index.js';
import { makeContext } from './tools/helpers.js';

/** Cache di lettura del processo (30 s, svuotata dalle scritture del server). */
export const READ_CACHE: ReadCache = new Map();
const READ_CACHE_TTL_MS = 30_000;

function json(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', ...headers } });
}

export interface McpDeps {
  now?: () => number;
  fetchImpl?: typeof fetch;
}

export async function handleMcp(request: Request, env: Env, props: Partial<SessionProps> | undefined, deps: McpDeps = {}): Promise<Response> {
  const nowMs = (deps.now ?? Date.now)();
  const denial = checkSession(props, env, Math.floor(nowMs / 1000));
  if (denial) {
    return json(denial.status, { error: denial.error, error_description: denial.message }, denial.status === 401 ? { 'WWW-Authenticate': `Bearer error="invalid_token", error_description="${denial.message}"` } : {});
  }
  if (request.method !== 'POST') {
    return json(405, { jsonrpc: '2.0', error: { code: -32000, message: 'Metodo non consentito' }, id: null }, { Allow: 'POST' });
  }
  const { uid, email } = props as SessionProps;
  const server = buildMcpServer(
    makeContext({ uid, email, db: makeFirestore(env, uid, deps.fetchImpl), cacheTtlMs: READ_CACHE_TTL_MS, cache: READ_CACHE, now: () => new Date(nowMs) })
  );
  const transport = new WebStandardStreamableHTTPServerTransport({ sessionIdGenerator: undefined, enableJsonResponse: true });
  await server.connect(transport);
  try {
    return await transport.handleRequest(request);
  } catch (error) {
    console.error('[mcp] errore', error instanceof Error ? error.message : 'sconosciuto');
    return json(500, { jsonrpc: '2.0', error: { code: -32603, message: 'Errore interno' }, id: null });
  }
}
