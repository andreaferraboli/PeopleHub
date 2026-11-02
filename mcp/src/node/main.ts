/**
 * Server MCP di PeopleHub come processo Node (VPS, servizio systemd dietro Traefik).
 * OAuth (@cloudflare/workers-oauth-provider) + /mcp, con KV su SQLite e limite di richieste in memoria.
 *
 * Configurazione da variabili d'ambiente (file /etc/peoplehub-mcp/env, vedi deploy/env.example):
 *   PUBLIC_URL (obbligatoria), HOST (127.0.0.1), PORT (18750), ALLOWED_EMAILS (elenco, niente jolly), FIREBASE_PROJECT_ID,
 *   MAX_SESSION_DAYS, STATE_DIRECTORY (la imposta systemd).
 * Secret come file (systemd LoadCredential, cartella $CREDENTIALS_DIRECTORY): google-sa.json, admin-token.
 * In alternativa GOOGLE_SA_FILE / ADMIN_TOKEN_FILE con il percorso del file.
 */
import { existsSync, mkdirSync, readFileSync } from 'node:fs';
import { createServer } from 'node:http';
import path from 'node:path';
import { DEFAULT_PROJECT_ID, type Env } from '../config.js';
import { createWorker } from '../worker.js';
import { createNodeHandler } from './http.js';
import { MemoryRateLimiter } from './rateLimit.js';
import { SqliteKV } from './sqliteKv.js';
function readSecret(credName: string, fileVar: string): string | undefined {
  const credDir = process.env.CREDENTIALS_DIRECTORY;
  const candidates = [credDir ? path.join(credDir, credName) : '', process.env[fileVar] ?? ''].filter(Boolean);
  for (const file of candidates) {
    if (existsSync(file)) {
      const value = readFileSync(file, 'utf8').trim();
      if (value) return value;
    }
  }
  return undefined;
}

export function loadEnv(): { env: Env; host: string; port: number; publicUrl: string; stateDir: string } {
  const publicUrl = (process.env.PUBLIC_URL ?? '').replace(/\/+$/, '');
  if (!/^https:\/\/[a-z0-9.-]+(:\d+)?$/.test(publicUrl) && !(process.env.ALLOW_LOOPBACK === 'true' && /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(publicUrl))) {
    throw new Error('PUBLIC_URL mancante o non valida (es. https://people-mcp.2-24-13-98.sslip.io)');
  }
  const stateDir = (process.env.STATE_DIRECTORY ?? '/var/lib/peoplehub-mcp').split(':')[0];
  mkdirSync(stateDir, { recursive: true, mode: 0o700 });
  // OAUTH_PROVIDER lo inietta la libreria OAuth a ogni richiesta
  const env: Omit<Env, 'OAUTH_PROVIDER'> = {
    OAUTH_KV: new SqliteKV(path.join(stateDir, 'oauth-kv.sqlite')) as unknown as KVNamespace,
    RATE_LIMIT: new MemoryRateLimiter(60, 60),
    PUBLIC_URL: publicUrl,
    ALLOWED_EMAILS: process.env.ALLOWED_EMAILS,
    FIREBASE_PROJECT_ID: process.env.FIREBASE_PROJECT_ID || DEFAULT_PROJECT_ID,
    MAX_SESSION_DAYS: process.env.MAX_SESSION_DAYS,
    ALLOW_LOOPBACK: process.env.ALLOW_LOOPBACK,
    GOOGLE_SA_JSON: readSecret('google-sa.json', 'GOOGLE_SA_FILE'),
    ADMIN_TOKEN: readSecret('admin-token', 'ADMIN_TOKEN_FILE'),
    FIRESTORE_EMULATOR_HOST: process.env.FIRESTORE_EMULATOR_HOST,
    FIREBASE_AUTH_EMULATOR_HOST: process.env.FIREBASE_AUTH_EMULATOR_HOST,
  };
  return { env: env as Env, host: process.env.HOST || '127.0.0.1', port: Number(process.env.PORT || 18750), publicUrl, stateDir };
}

async function main(): Promise<void> {
  const { env, host, port, publicUrl } = loadEnv();
  if (!env.GOOGLE_SA_JSON && !env.FIRESTORE_EMULATOR_HOST) console.warn('[avvio] chiave del service account assente: i tool falliranno alla prima lettura');
  const handler = createNodeHandler(createWorker(), env, { publicUrl });
  const server = createServer({ requestTimeout: 60_000, headersTimeout: 20_000, keepAliveTimeout: 30_000 }, (req, res) => void handler(req, res));
  server.listen(port, host, () => console.log(`[avvio] PeopleHub MCP su http://${host}:${port}, pubblico ${publicUrl}/mcp`));

  const stop = (signal: string) => {
    console.log(`[stop] ${signal}`);
    server.close(async () => {
      await handler.drain();
      (env.OAUTH_KV as unknown as SqliteKV).close();
      process.exit(0);
    });
    server.closeIdleConnections();
    setTimeout(() => process.exit(0), 10_000).unref();
  };
  process.on('SIGTERM', () => stop('SIGTERM'));
  process.on('SIGINT', () => stop('SIGINT'));
}

main().catch((error) => {
  console.error('[avvio] errore:', error instanceof Error ? error.message : error);
  process.exit(1);
});
