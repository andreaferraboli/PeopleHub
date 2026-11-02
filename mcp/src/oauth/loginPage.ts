/**
 * Pagina di accesso mostrata da /authorize: accesso con Google (Firebase Auth)
 * e conferma esplicita del collegamento. Il codice del browser sta in
 * `web/login.ts` (impacchettato in public/mcp-assets/login.js).
 */
import { FIREBASE_WEB_CONFIG } from '../config.js';

export interface LoginPageInput {
  /** Nonce del CSP per lo <style> in linea. */
  nonce: string;
  /** Handle del consenso (libreria OAuth): da rimandare a /oauth/login. */
  handle: string;
  clientName: string;
  redirectHost: string;
  /** Il ritorno va a un'app locale (localhost): la pagina avvisa. */
  redirectIsLoopback: boolean;
  /** Solo sviluppo: host dell'Auth emulator. */
  authEmulatorHost?: string;
}

/** JSON dentro un <script>: `<` e' escapato per non poter chiudere il tag. */
function jsonForScript(value: unknown): string {
  return JSON.stringify(value)
    .replace(/</g, '\\u003c')
    .replace(new RegExp(String.fromCharCode(0x2028), 'g'), '\\u2028')
    .replace(new RegExp(String.fromCharCode(0x2029), 'g'), '\\u2029');
}

/**
 * CSP della pagina di accesso: solo gli script della stessa origine piu' quelli
 * di Google che l'accesso con popup di Firebase Auth carica (apis.google.com),
 * connessioni solo verso le API di Firebase/Google e verso la stessa origine,
 * frame solo dell'handler di Firebase Auth. Niente form, niente <base>, niente embed.
 */
export function buildLoginCsp(authEmulatorHost: string | undefined, nonce: string): string {
  const authDomain = `https://${FIREBASE_WEB_CONFIG.authDomain}`;
  const emulator = authEmulatorHost ? `http://${authEmulatorHost}` : '';
  const directives: Record<string, string[]> = {
    'default-src': ["'none'"],
    'script-src': ["'self'", 'https://apis.google.com', 'https://www.gstatic.com'],
    'style-src': [`'nonce-${nonce}'`],
    'connect-src': ["'self'", 'https://identitytoolkit.googleapis.com', 'https://securetoken.googleapis.com', 'https://www.googleapis.com', ...(emulator ? [emulator] : [])],
    'frame-src': [authDomain, 'https://accounts.google.com', 'https://content.googleapis.com', ...(emulator ? [emulator] : [])],
    'img-src': ["'self'", 'data:'],
    'base-uri': ["'none'"],
    'form-action': ["'none'"],
    'object-src': ["'none'"],
    'frame-ancestors': ["'none'"],
  };
  return Object.entries(directives)
    .map(([k, v]) => `${k} ${v.join(' ')}`)
    .join('; ');
}

export function renderLoginPage(input: LoginPageInput): string {
  const { nonce, handle, clientName, redirectHost, redirectIsLoopback } = input;
  const boot = {
    handle,
    clientName,
    redirectHost,
    redirectIsLoopback,
    firebase: FIREBASE_WEB_CONFIG,
    authEmulatorHost: input.authEmulatorHost ?? null,
  };
  return `<!doctype html>
<html lang="it">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>PeopleHub: collega Claude</title>
<style nonce="${nonce}">
:root{--bg:#f6f1e7;--card:#fffdf8;--ink:#1d1a16;--muted:#6b6358;--brand:#1d1a16;--brand-ink:#FFC83D;--accent:#c98f00;--line:#e5ddcf;--danger:#b42318}
@media (prefers-color-scheme:dark){:root{--bg:#121014;--card:#1b181e;--ink:#f3eee6;--muted:#a59d93;--brand:#FFC83D;--brand-ink:#1d1a16;--accent:#FFC83D;--line:#2c2830;--danger:#f97066}}
*{box-sizing:border-box}
body{margin:0;min-height:100vh;display:grid;place-items:center;background:var(--bg);color:var(--ink);font:16px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;padding:16px}
main{width:100%;max-width:420px;background:var(--card);border:1px solid var(--line);border-radius:16px;padding:28px 24px;box-shadow:0 1px 2px rgba(0,0,0,.04)}
.logo{width:44px;height:44px;border-radius:12px;background:var(--brand);color:var(--brand-ink);display:grid;place-items:center;font-weight:700;font-size:20px;margin-bottom:16px}
h1{font-size:1.35rem;margin:0 0 6px}
p{margin:0 0 14px;color:var(--muted)}
.unverified{background:var(--bg);border:1px solid var(--accent);border-radius:10px;padding:10px 12px;margin:0 0 14px;color:var(--ink)}
.dest{display:block;font-size:1.15rem;font-weight:700;word-break:break-all;margin-top:2px}
.who{background:var(--bg);border:1px solid var(--line);border-radius:10px;padding:10px 12px;margin:0 0 16px;color:var(--ink);word-break:break-all}
button{appearance:none;border:0;border-radius:10px;padding:12px 16px;font:inherit;font-weight:600;cursor:pointer;width:100%}
button.primary{background:var(--brand);color:var(--brand-ink)}
button.ghost{background:transparent;color:var(--muted);margin-top:8px}
button:disabled{opacity:.6;cursor:default}
button:focus-visible{outline:3px solid var(--accent);outline-offset:2px}
.err{color:var(--danger);margin:12px 0 0;min-height:1.5em}
.small{font-size:.85rem}
[hidden]{display:none!important}
</style>
</head>
<body>
<main>
  <div class="logo" aria-hidden="true">PH</div>
  <h1>PeopleHub</h1>
  <section id="step-signin">
    <p class="unverified"><strong id="client-name"></strong><br>chiede di collegarsi ai tuoi dati di PeopleHub: persone, compleanni, uscite, eventi e promemoria, in lettura e in scrittura. Dopo l'accesso tornerai su:<span class="dest" id="redirect-host-1"></span></p>
    <button id="btn-google" class="primary" type="button">Accedi con Google</button>
  </section>
  <section id="step-consent" hidden>
    <p>Accesso eseguito come:</p>
    <div class="who" id="who"></div>
    <p class="unverified"><strong id="client-name-2"></strong><br>potrà leggere e modificare tutti i dati PeopleHub di questo account. Dopo il collegamento tornerai su:<span class="dest" id="redirect-host"></span><span id="loopback-warning" hidden><br><strong>Questo invia l'accesso a un'app sul tuo computer.</strong> Continua solo se l'hai appena avviata tu.</span></p>
    <button id="btn-allow" class="primary" type="button">Autorizza</button>
    <button id="btn-deny" class="ghost" type="button">Annulla</button>
    <p class="small">Se non hai avviato tu questo collegamento, premi Annulla.</p>
  </section>
  <p class="err" id="error" role="alert"></p>
</main>
<script type="application/json" id="boot">${jsonForScript(boot)}</script>
<noscript><p style="text-align:center">Serve JavaScript per accedere.</p></noscript>
<script type="module" src="/mcp-assets/login.js"></script>
</body>
</html>`;
}
