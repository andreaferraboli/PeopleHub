/**
 * Accesso a Firestore: REST con service account, confinato a users/{uid} dell'utente del token.
 */
import { DEFAULT_PROJECT_ID, type Env } from './config.js';
import { Firestore, ServiceAccountAuth } from './firestore/index.js';

const authCache = new Map<string, ServiceAccountAuth>();

/** Solo users/{uid}/... dell'utente autenticato: nessun'altra radice. */
export function allowedPaths(uid: string): (segments: string[]) => boolean {
  return (segments) => segments[0] === 'users' && segments[1] === uid && segments.length >= 2;
}

export function makeFirestore(env: Env, uid: string, fetchImpl?: typeof fetch): Firestore {
  const projectId = env.FIREBASE_PROJECT_ID || DEFAULT_PROJECT_ID;
  const allow = allowedPaths(uid);
  if (env.FIRESTORE_EMULATOR_HOST) {
    // Solo sviluppo: emulatore, "Bearer owner" aggira le regole.
    return new Firestore({ projectId, baseUrl: `http://${env.FIRESTORE_EMULATOR_HOST}`, getToken: async () => 'owner', allow, fetchImpl });
  }
  if (!env.GOOGLE_SA_JSON) {
    return new Firestore({ projectId, allow, fetchImpl, getToken: async () => { throw new Error('GOOGLE_SA_JSON non configurato'); } });
  }
  let auth = authCache.get(env.GOOGLE_SA_JSON);
  if (!auth) {
    auth = ServiceAccountAuth.fromJson(env.GOOGLE_SA_JSON, fetchImpl);
    authCache.set(env.GOOGLE_SA_JSON, auth);
  }
  return new Firestore({ projectId, getToken: auth.getToken, allow, fetchImpl });
}
