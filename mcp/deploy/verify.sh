#!/usr/bin/env bash
# Verifica da fuori del server in linea: discovery OAuth, 401 su /mcp, registrazione dinamica col callback di Claude,
# pagina di login. Con --read anche letture sui dati veri (solo conteggi) con un token coniato da /admin/mint e poi revocato.
# Con --write in piu crea, rilegge e cancella (tombstone) una persona e un check-in su un uid di TEST (PH_UID, default
# mcp-smoke-test), mai quello vero. Uso (sulla VPS, da root): bash mcp/deploy/verify.sh [--read|--write]
set -euo pipefail
MCP="$(cd "$(dirname "$0")/.." && pwd)"
ETC=/etc/peoplehub-mcp
BASE="$(sed -n 's/^PUBLIC_URL=//p' "$ETC/env" 2>/dev/null | tail -1)"; BASE="${BASE:-https://people-mcp.2-24-13-98.sslip.io}"
ok() { printf '  ok  %s\n' "$1"; }
ko() { printf '  KO  %s\n' "$1"; FAIL=1; }
FAIL=0
echo "Server: $BASE"
systemctl is-active --quiet peoplehub-mcp && ok "systemctl is-active peoplehub-mcp" || ko "servizio peoplehub-mcp non attivo"
curl -fsS "$BASE/.well-known/oauth-authorization-server" | grep -q "\"issuer\":\"$BASE\"" && ok "oauth-authorization-server (issuer)" || ko "oauth-authorization-server"
curl -fsS "$BASE/.well-known/oauth-protected-resource/mcp" | grep -q "\"resource\":\"$BASE/mcp\"" && ok "oauth-protected-resource/mcp" || ko "oauth-protected-resource/mcp"
H="$(curl -sS -o /dev/null -D - -X POST "$BASE/mcp" -H 'content-type: application/json' -d '{}')"
echo "$H" | head -1 | grep -q ' 401' && echo "$H" | grep -qi "www-authenticate: Bearer.*resource_metadata=\"$BASE/.well-known/oauth-protected-resource/mcp\"" && ok "POST /mcp senza token: 401 + WWW-Authenticate" || ko "POST /mcp senza token"
R="$(curl -sS -w '\n%{http_code}' -X POST "$BASE/register" -H 'content-type: application/json' -d '{"redirect_uris":["https://claude.ai/api/mcp/auth_callback"],"client_name":"verify","token_endpoint_auth_method":"none","grant_types":["authorization_code","refresh_token"]}')"
CID="$(echo "$R" | head -1 | sed -n 's/.*"client_id":"\([^"]*\)".*/\1/p')"
[ "$(echo "$R" | tail -1)" = 201 ] && [ -n "$CID" ] && ok "DCR col callback di Claude (201)" || ko "DCR"
[ "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$BASE/register" -H 'content-type: application/json' -d '{"redirect_uris":["https://evil.example/cb"],"token_endpoint_auth_method":"none"}')" = 400 ] && ok "DCR con redirect estraneo rifiutata (400)" || ko "DCR redirect estraneo"
if [ -n "$CID" ]; then
  Q="response_type=code&client_id=$CID&redirect_uri=https%3A%2F%2Fclaude.ai%2Fapi%2Fmcp%2Fauth_callback&scope=people&state=x&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256&resource=$BASE/mcp"
  P="$(curl -sS -D - "$BASE/authorize?$Q")"
  echo "$P" | head -1 | grep -q ' 200' && echo "$P" | grep -qi "content-security-policy: default-src 'none'" && echo "$P" | grep -q '"authDomain":"peoplehub-af26.firebaseapp.com"' && ok "pagina /authorize (200, CSP, Firebase peoplehub-af26)" || ko "pagina /authorize"
fi
[ "$(curl -sS -o /dev/null -w '%{http_code}' "$BASE/mcp-assets/login.js")" = 200 ] && ok "script di login" || ko "script di login"

if [ "${1:-}" = "--read" ] || [ "${1:-}" = "--write" ]; then
  cd "$MCP"
  EMAIL="$(sed -n 's/^ALLOWED_EMAILS=//p' "$ETC/env" | tail -1 | cut -d, -f1)"
  TOKEN_FILE="$(mktemp)"; trap 'rm -f "$TOKEN_FILE"' EXIT
  ADMIN_TOKEN_FILE="$ETC/admin-token" node scripts/mint-token.mjs --url "$BASE" --uid "${PH_UID:-mcp-smoke-test}" --email "$EMAIL" > "$TOKEN_FILE"
  if [ "$1" = "--write" ]; then
    MCP_TOKEN="$(cat "$TOKEN_FILE")" node scripts/smoke.mjs "$BASE/mcp" --write || FAIL=1
  else
    MCP_TOKEN="$(cat "$TOKEN_FILE")" node scripts/smoke.mjs "$BASE/mcp" || FAIL=1
  fi
  ADMIN_TOKEN_FILE="$ETC/admin-token" node scripts/revoke.mjs --url "$BASE" --uid "${PH_UID:-mcp-smoke-test}" --mint-only
fi
exit "$FAIL"
