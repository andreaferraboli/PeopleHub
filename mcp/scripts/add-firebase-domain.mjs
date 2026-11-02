// Aggiunge un dominio ai "domini autorizzati" di Firebase Auth (progetto peoplehub-af26) SENZA rimuovere quelli esistenti.
// Serve per il login Google dalla pagina di accesso del server (VPS: people-mcp.2-24-13-98.sslip.io). Usa le credenziali della CLI firebase.
// Uso: node mcp/scripts/add-firebase-domain.mjs <dominio> [--dry-run]
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

const domain = process.argv[2];
const dry = process.argv.includes('--dry-run');
if (!domain || !/^[a-z0-9.-]+$/.test(domain)) throw new Error('Uso: add-firebase-domain.mjs <dominio> [--dry-run]');
const project = 'peoplehub-af26';
const readToken = () => JSON.parse(readFileSync(`${process.env.HOME}/.config/configstore/firebase-tools.json`, 'utf8')).tokens.access_token;
const url = `https://identitytoolkit.googleapis.com/admin/v2/projects/${project}/config`;
let headers = { Authorization: `Bearer ${readToken()}`, 'x-goog-user-project': project, 'content-type': 'application/json' };

let res0 = await fetch(url, { headers });
if (res0.status === 401) {
  // access token della CLI scaduto: un comando qualunque della CLI lo rinnova (refresh token salvato)
  execFileSync('firebase', ['projects:list', '--json'], { stdio: 'ignore' });
  headers = { ...headers, Authorization: `Bearer ${readToken()}` };
  res0 = await fetch(url, { headers });
}
const current = await res0.json();
if (!Array.isArray(current.authorizedDomains)) throw new Error(`Lettura non riuscita: ${JSON.stringify(current).slice(0, 200)}`);
const merged = [...new Set([...current.authorizedDomains, domain])];
if (merged.length === current.authorizedDomains.length) {
  console.log(`${domain} e gia tra i domini autorizzati (${current.authorizedDomains.length} in tutto).`);
} else if (dry) {
  console.log(`[dry-run] aggiungerei ${domain}; elenco risultante: ${merged.join(', ')}`);
} else {
  const res = await fetch(`${url}?updateMask=authorizedDomains`, { method: 'PATCH', headers, body: JSON.stringify({ authorizedDomains: merged }) });
  const body = await res.json();
  if (!res.ok) throw new Error(`Aggiornamento non riuscito (HTTP ${res.status}): ${JSON.stringify(body).slice(0, 300)}`);
  const lost = current.authorizedDomains.filter((d) => !body.authorizedDomains.includes(d));
  if (lost.length) throw new Error(`ATTENZIONE: domini spariti dopo l'aggiornamento: ${lost.join(', ')}`);
  console.log(`Aggiunto ${domain}. Domini autorizzati ora: ${body.authorizedDomains.join(', ')}`);
}
