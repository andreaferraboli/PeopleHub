/**
 * Costanti e configurazione del server MCP di PeopleHub.
 * I parametri (allowlist, URL pubblico, progetto) arrivano dalle variabili d'ambiente (/etc/peoplehub-mcp/env).
 */

export const DEFAULT_ALLOWED_EMAILS = 'andrew.ferro04@gmail.com';
export const DEFAULT_PROJECT_ID = 'peoplehub-af26';

/** Config web Firebase di peoplehub-af26 (app web "PeopleHub MCP login"): e' pubblica, finisce nella pagina di login. */
export const FIREBASE_WEB_CONFIG = {
  apiKey: 'AIzaSyAatfcd1vJSQtDpA85SyKOI-sKfz3OMTlk',
  authDomain: 'peoplehub-af26.firebaseapp.com',
  projectId: 'peoplehub-af26',
  appId: '1:205831119141:web:bb3f1bccfdfadc9c4e7acc',
};

export const SCOPE_ACCESS = 'people';
export const SCOPE_OFFLINE = 'offline_access';

export function parseAllowlist(raw: string | undefined): Set<string> {
  return new Set(
    (raw ?? DEFAULT_ALLOWED_EMAILS)
      .split(/[\s,;]+/)
      .map((e) => e.trim().toLowerCase())
      .filter(Boolean)
  );
}

/**
 * Email ammessa: solo quelle in elenco. Il jolly `*` qui NON vale (scelta voluta: dati personali di una sola persona).
 * Email verificata, provider google.com, login recente e scadenza della sessione restano controlli a parte.
 */
export function isEmailAllowed(raw: string | undefined, email: string): boolean {
  const list = parseAllowlist(raw);
  list.delete('*');
  return list.has(email.trim().toLowerCase());
}

export interface Env {
  /** Namespace KV dello stato OAuth (client, grant, token, codici). Sulla VPS e' SQLite. */
  OAUTH_KV: KVNamespace;
  /** Helper della libreria OAuth (iniettati dal provider). */
  OAUTH_PROVIDER: import('@cloudflare/workers-oauth-provider').OAuthHelpers;
  /** Origine pubblica (https://people-mcp.2-24-13-98.sslip.io). */
  PUBLIC_URL?: string;
  /** Email Google ammesse (separate da virgola): controllate al login e a ogni richiesta /mcp. */
  ALLOWED_EMAILS?: string;
  /** Progetto Firebase (default peoplehub-af26). */
  FIREBASE_PROJECT_ID?: string;
  /** "true" = accetta redirect loopback (solo sviluppo). */
  ALLOW_LOOPBACK?: string;
  /** Giorni dopo i quali un accesso Google non basta piu e va rifatto il login (default 30). */
  MAX_SESSION_DAYS?: string;
  /** Service account (JSON) per le API Firestore: SECRET. */
  GOOGLE_SA_JSON?: string;
  /** Token per gli endpoint /admin (revoca, coniazione di verifica): SECRET, opzionale. */
  ADMIN_TOKEN?: string;
  /** Solo sviluppo: host degli emulatori. */
  FIRESTORE_EMULATOR_HOST?: string;
  FIREBASE_AUTH_EMULATOR_HOST?: string;
  /** Limite di richieste per IP sulle rotte OAuth. */
  RATE_LIMIT?: RateLimiter;
}

export interface RateLimiter {
  limit(options: { key: string }): Promise<{ success: boolean; retryAfterSec?: number }>;
}

export interface SessionProps extends Record<string, unknown> {
  uid: string;
  email: string;
  provider: string;
  /** Ultimo accesso interattivo con Google (epoch secondi, dal token Firebase). */
  authTime: number;
}
