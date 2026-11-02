/**
 * Codice della pagina di accesso (gira nel browser). Passi:
 *  1. "Accedi con Google" (Firebase Auth, popup; se bloccato, redirect);
 *  2. conferma esplicita ("Autorizza" / "Annulla");
 *  3. POST /oauth/login con l'ID token e la richiesta firmata, poi redirect a Claude.
 */
import { initializeApp, type FirebaseOptions } from 'firebase/app';
import {
  GoogleAuthProvider,
  browserSessionPersistence,
  connectAuthEmulator,
  getAuth,
  getRedirectResult,
  setPersistence,
  signInWithPopup,
  signInWithRedirect,
  signOut,
  type User,
} from 'firebase/auth';

interface Boot {
  handle: string;
  redirectIsLoopback: boolean;
  clientName: string;
  redirectHost: string;
  firebase: FirebaseOptions;
  authEmulatorHost: string | null;
}

const $ = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
const boot: Boot = JSON.parse($('boot').textContent || '{}');

const errorBox = $('error');
const stepSignin = $('step-signin');
const stepConsent = $('step-consent');
const btnGoogle = $<HTMLButtonElement>('btn-google');
const btnAllow = $<HTMLButtonElement>('btn-allow');
const btnDeny = $<HTMLButtonElement>('btn-deny');

// Il nome lo sceglie chi registra il client: va mostrato come non verificato.
const label = `Applicazione non verificata: ${boot.clientName}`;
$('client-name').textContent = label;
$('client-name-2').textContent = label;
$('redirect-host').textContent = boot.redirectHost;
$('redirect-host-1').textContent = boot.redirectHost;
if (boot.redirectIsLoopback) $('loopback-warning').hidden = false;

const app = initializeApp(boot.firebase);
const auth = getAuth(app);
if (boot.authEmulatorHost) connectAuthEmulator(auth, `http://${boot.authEmulatorHost}`, { disableWarnings: true });

const provider = new GoogleAuthProvider();
provider.setCustomParameters({ prompt: 'select_account' });

let currentUser: User | null = null;

function showError(message: string) {
  errorBox.textContent = message;
}

function showConsent(user: User) {
  currentUser = user;
  $('who').textContent = user.email ?? user.uid;
  stepSignin.hidden = true;
  stepConsent.hidden = false;
  errorBox.textContent = '';
}

function authErrorMessage(error: unknown): string {
  const code = (error as { code?: string }).code ?? '';
  if (code === 'auth/popup-closed-by-user' || code === 'auth/cancelled-popup-request') return 'Accesso annullato.';
  if (code === 'auth/network-request-failed') return 'Connessione assente, riprova.';
  return 'Accesso con Google non riuscito. Riprova.';
}

async function postLogin(payload: Record<string, unknown>): Promise<{ redirectTo?: string; message?: string }> {
  const response = await fetch('/oauth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'same-origin',
    body: JSON.stringify({ handle: boot.handle, ...payload }),
  });
  const body = (await response.json().catch(() => ({}))) as { redirectTo?: string; message?: string };
  if (!response.ok) throw Object.assign(new Error(body.message || 'Richiesta non riuscita'), { body });
  return body;
}

btnGoogle.addEventListener('click', async () => {
  btnGoogle.disabled = true;
  errorBox.textContent = '';
  try {
    await setPersistence(auth, browserSessionPersistence);
    try {
      const result = await signInWithPopup(auth, provider);
      showConsent(result.user);
    } catch (error) {
      const code = (error as { code?: string }).code;
      if (code === 'auth/popup-blocked') {
        await signInWithRedirect(auth, provider);
        return;
      }
      throw error;
    }
  } catch (error) {
    showError(authErrorMessage(error));
  } finally {
    btnGoogle.disabled = false;
  }
});

btnAllow.addEventListener('click', async () => {
  if (!currentUser) return;
  btnAllow.disabled = true;
  btnDeny.disabled = true;
  errorBox.textContent = '';
  try {
    const idToken = await currentUser.getIdToken(true);
    const { redirectTo } = await postLogin({ idToken });
    if (!redirectTo) throw new Error('Risposta non valida');
    await signOut(auth).catch(() => undefined);
    window.location.assign(redirectTo);
  } catch (error) {
    showError((error as Error).message || 'Autorizzazione non riuscita.');
    btnAllow.disabled = false;
    btnDeny.disabled = false;
    // Account non autorizzato: si torna alla scelta dell'account.
    if ((error as { body?: { error?: string } }).body?.error === 'not_allowed') {
      await signOut(auth).catch(() => undefined);
      currentUser = null;
      stepConsent.hidden = true;
      stepSignin.hidden = false;
    }
  }
});

btnDeny.addEventListener('click', async () => {
  btnAllow.disabled = true;
  btnDeny.disabled = true;
  try {
    const { redirectTo } = await postLogin({ deny: true });
    await signOut(auth).catch(() => undefined);
    if (redirectTo) window.location.assign(redirectTo);
  } catch (error) {
    showError((error as Error).message);
    btnAllow.disabled = false;
    btnDeny.disabled = false;
  }
});

// Ritorno da un accesso con redirect (popup bloccato).
getRedirectResult(auth)
  .then((result) => {
    if (result?.user) showConsent(result.user);
  })
  .catch((error) => showError(authErrorMessage(error)));
