/**
 * Regole sull'identita' che valgono sia al login sia a ogni richiesta /mcp:
 * email verificata e in allowlist (niente jolly), provider google.com, accesso interattivo recente.
 */
import { isEmailAllowed, type Env, type SessionProps } from '../config.js';
import type { VerifiedIdentity } from './firebaseToken.js';

/** Eta' massima dell'accesso Google al momento del login (la pagina ne forza uno nuovo). */
export const MAX_LOGIN_AUTH_AGE_SEC = 10 * 60;

export type Denial = { status: number; error: string; message: string };

export function checkIdentityAtLogin(identity: VerifiedIdentity, env: Env, nowSec: number): Denial | null {
  if (!identity.emailVerified || !identity.email) return { status: 403, error: 'email_not_verified', message: 'Email non verificata.' };
  if (identity.provider !== 'google.com') return { status: 403, error: 'provider_not_allowed', message: "Serve l'accesso con Google." };
  if (!isEmailAllowed(env.ALLOWED_EMAILS, identity.email)) return { status: 403, error: 'not_allowed', message: "Questo account Google non è autorizzato." };
  if (!identity.authTime || nowSec - identity.authTime > MAX_LOGIN_AUTH_AGE_SEC) return { status: 401, error: 'stale_login', message: 'Accesso troppo vecchio, accedi di nuovo.' };
  return null;
}

/** Controllo a ogni richiesta /mcp, sui dati salvati nel grant: l'allowlist e' quella CORRENTE del Worker. */
export function checkSession(props: Partial<SessionProps> | undefined, env: Env, nowSec: number): Denial | null {
  if (!props?.uid || !props.email) return { status: 401, error: 'invalid_token', message: 'Sessione non valida.' };
  if (props.provider !== 'google.com') return { status: 403, error: 'provider_not_allowed', message: 'Accesso non ammesso.' };
  if (!isEmailAllowed(env.ALLOWED_EMAILS, props.email)) return { status: 403, error: 'not_allowed', message: 'Utente non più autorizzato.' };
  const maxDays = Number(env.MAX_SESSION_DAYS ?? 30);
  if (!props.authTime || nowSec - props.authTime > maxDays * 86_400) return { status: 401, error: 'session_expired', message: 'Sessione scaduta: rifai il collegamento da Claude.' };
  return null;
}
