/**
 * Verifica degli ID token Firebase nel Worker (nessun Admin SDK): firma RS256 con le
 * chiavi pubbliche Google (JWKS di securetoken), poi issuer, audience, scadenza,
 * email verificata e provider.
 */
import { createRemoteJWKSet, decodeJwt, jwtVerify } from 'jose';
import { DEFAULT_PROJECT_ID, type Env } from '../config.js';

const JWKS_URL = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';
let jwks: ReturnType<typeof createRemoteJWKSet> | undefined;

export interface VerifiedIdentity {
  uid: string;
  email: string;
  emailVerified: boolean;
  provider: string;
  /** Epoch secondi dell'ultimo accesso interattivo. */
  authTime: number;
}

export type IdTokenVerifier = (idToken: string, env: Env) => Promise<VerifiedIdentity>;

export const verifyFirebaseIdToken: IdTokenVerifier = async (idToken, env) => {
  const project = env.FIREBASE_PROJECT_ID || DEFAULT_PROJECT_ID;
  let claims: Record<string, unknown>;
  if (env.FIREBASE_AUTH_EMULATOR_HOST) {
    // Solo sviluppo: l'Auth emulator firma con "alg: none". Mai attivo in produzione (variabile assente).
    claims = decodeJwt(idToken);
    if (claims.iss !== `https://securetoken.google.com/${project}` || claims.aud !== project) throw new Error('issuer/audience');
    if (typeof claims.exp !== 'number' || claims.exp < Date.now() / 1000) throw new Error('scaduto');
  } else {
    jwks ??= createRemoteJWKSet(new URL(JWKS_URL));
    const { payload } = await jwtVerify(idToken, jwks, {
      algorithms: ['RS256'],
      issuer: `https://securetoken.google.com/${project}`,
      audience: project,
      clockTolerance: 10,
    });
    claims = payload as Record<string, unknown>;
  }
  const firebase = (claims.firebase ?? {}) as { sign_in_provider?: string };
  const uid = typeof claims.sub === 'string' ? claims.sub : '';
  if (!uid || uid.length > 128) throw new Error('sub non valido');
  return {
    uid,
    email: typeof claims.email === 'string' ? claims.email.toLowerCase() : '',
    emailVerified: claims.email_verified === true,
    provider: firebase.sign_in_provider ?? '',
    authTime: typeof claims.auth_time === 'number' ? claims.auth_time : 0,
  };
};
