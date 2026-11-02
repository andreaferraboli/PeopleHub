// Conia un access token del server MCP per un utente in allowlist, SOLO per verifiche (letture e scritture di prova).
// Passa dall'endpoint protetto /admin/mint (serve ADMIN_TOKEN): il token nasce dal flusso OAuth vero e si revoca con revoke.mjs.
// Uso: ADMIN_TOKEN=... node mcp/scripts/mint-token.mjs --url https://people-mcp.2-24-13-98.sslip.io --uid <uid> --email <email>
// (ADMIN_TOKEN puo stare in un file: ADMIN_TOKEN_FILE=/etc/peoplehub-mcp/admin-token). Stampa solo l'access token.
import { readFileSync } from 'node:fs';

const args = Object.fromEntries(process.argv.slice(2).reduce((acc, v, i, a) => (v.startsWith('--') ? [...acc, [v.slice(2), a[i + 1]]] : acc), []));
const adminToken = process.env.ADMIN_TOKEN ?? readFileSync(process.env.ADMIN_TOKEN_FILE ?? "/etc/peoplehub-mcp/admin-token", 'utf8').trim();
if (!args.url || !args.uid || !args.email) throw new Error('--url, --uid e --email obbligatori');
const res = await fetch(`${args.url.replace(/\/+$/, '')}/admin/mint`, {
  method: 'POST',
  headers: { 'content-type': 'application/json', authorization: `Bearer ${adminToken}` },
  body: JSON.stringify({ uid: args.uid, email: args.email }),
});
if (!res.ok) throw new Error(`mint fallito: HTTP ${res.status}`);
process.stdout.write((await res.json()).access_token);
