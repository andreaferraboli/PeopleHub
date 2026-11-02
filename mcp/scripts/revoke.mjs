// Revoca tutti i token (access e refresh) di un utente, o di tutti gli utenti noti: serve un nuovo collegamento da Claude.
// Uso: node mcp/scripts/revoke.mjs --url https://people-mcp.2-24-13-98.sslip.io [--email a@b.c | --uid <uid> | --all] [--mint-only]
// --mint-only: solo i token coniati per le verifiche (non i collegamenti veri di Claude).
// Il token admin sta in ADMIN_TOKEN o nel file ADMIN_TOKEN_FILE (default /etc/peoplehub-mcp/admin-token).
import { readFileSync } from 'node:fs';

const argv = process.argv.slice(2);
const args = Object.fromEntries(argv.reduce((acc, v, i, a) => (v.startsWith('--') && !a[i + 1]?.startsWith('--') ? [...acc, [v.slice(2), a[i + 1]]] : acc), []));
const adminToken = process.env.ADMIN_TOKEN ?? readFileSync(process.env.ADMIN_TOKEN_FILE ?? '/etc/peoplehub-mcp/admin-token', 'utf8').trim();
if (!args.url || !(args.email || args.uid || argv.includes('--all'))) throw new Error('--url e (--email <email>, --uid <uid> oppure --all) obbligatori');
const target = args.email ? { email: args.email } : args.uid ? { uid: args.uid } : { all: true };
const res = await fetch(`${args.url.replace(/\/+$/, '')}/admin/revoke`, {
  method: 'POST',
  headers: { 'content-type': 'application/json', authorization: `Bearer ${adminToken}` },
  body: JSON.stringify({ ...target, ...(argv.includes('--mint-only') ? { mintOnly: true } : {}) }),
});
console.log(res.status, JSON.stringify(await res.json()));
