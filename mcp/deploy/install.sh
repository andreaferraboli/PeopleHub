#!/usr/bin/env bash
# Installa o aggiorna il server MCP di PeopleHub sulla VPS. Idempotente: si rilancia dopo ogni modifica al codice.
# Non tocca gli altri servizi: aggiunge l'unita' systemd peoplehub-mcp e un container-etichetta per il Traefik esistente.
# Uso (da root): bash mcp/deploy/install.sh [--skip-tests] [--skip-firebase]
set -euo pipefail
MCP="$(cd "$(dirname "$0")/.." && pwd)"
DEPLOY="$MCP/deploy"
APP=/opt/peoplehub-mcp/app
ROUTE=/opt/peoplehub-mcp/route
ETC=/etc/peoplehub-mcp
SVC_USER=peoplemcp
SKIP_TESTS=0; SKIP_FIREBASE=0
for a in "$@"; do
  case "$a" in
    --skip-tests) SKIP_TESTS=1 ;;
    --skip-firebase) SKIP_FIREBASE=1 ;;
    *) echo "opzione sconosciuta: $a" >&2; exit 2 ;;
  esac
done
[ "$(id -u)" = 0 ] || { echo "serve root" >&2; exit 1; }
[ -x /usr/bin/node ] || { echo "manca /usr/bin/node (Node 22+)" >&2; exit 1; }

# 1. build (un solo file, nessun node_modules a runtime)
cd "$MCP"
npm ci --no-audit --no-fund --loglevel=error
node build.mjs --node
[ "$SKIP_TESTS" = 1 ] || npx vitest run --reporter=dot

# 2. utente di sistema senza shell ne home
id "$SVC_USER" >/dev/null 2>&1 || useradd --system --user-group --no-create-home \
  --home-dir /nonexistent --shell /usr/sbin/nologin "$SVC_USER"

# 3. codice
install -d -m 755 /opt/peoplehub-mcp "$APP" "$ROUTE"
install -m 644 "$MCP/dist/server.mjs" "$APP/server.mjs"
install -m 644 "$MCP/dist/server.mjs.map" "$APP/server.mjs.map"

# 4. configurazione e secret (mai stampati, mai sovrascritti se gia presenti)
install -d -m 700 -o root -g root "$ETC"
[ -f "$ETC/env" ] || install -m 600 -o "$SVC_USER" -g "$SVC_USER" "$DEPLOY/env.example" "$ETC/env"
chown "$SVC_USER:$SVC_USER" "$ETC/env"; chmod 600 "$ETC/env"
[ -s "$ETC/google-sa.json" ] || echo "ATTENZIONE: manca $ETC/google-sa.json (chiave del service account di peoplehub-af26): i tool non leggeranno Firestore" >&2
if [ ! -s "$ETC/admin-token" ]; then
  ( umask 077; node -e 'process.stdout.write(require("crypto").randomBytes(32).toString("base64url"))' > "$ETC/admin-token" )
  echo "token admin generato in $ETC/admin-token (non viene stampato)"
fi
# LoadCredential vuole il file presente: se la chiave manca, un file vuoto (il server lo tratta come assente)
[ -e "$ETC/google-sa.json" ] || install -m 600 /dev/null "$ETC/google-sa.json"
chown root:root "$ETC/google-sa.json" "$ETC/admin-token"; chmod 600 "$ETC/google-sa.json" "$ETC/admin-token"

# 5. servizio
install -m 644 "$DEPLOY/peoplehub-mcp.service" /etc/systemd/system/peoplehub-mcp.service
systemctl daemon-reload
systemctl enable peoplehub-mcp >/dev/null 2>&1
systemctl restart peoplehub-mcp

# 6. rotta Traefik (TLS automatico Let's Encrypt)
install -m 644 "$DEPLOY/route/docker-compose.yml" "$ROUTE/docker-compose.yml"
docker compose -p peoplehub-mcp-route -f "$ROUTE/docker-compose.yml" up -d --quiet-pull

# 7. il processo risponde in locale
PORT="$(sed -n 's/^PORT=//p' "$ETC/env" | tail -1)"; PORT="${PORT:-18750}"
for _ in $(seq 1 20); do curl -fsS -o /dev/null "http://127.0.0.1:$PORT/" && break; sleep 0.5; done
curl -fsS -o /dev/null "http://127.0.0.1:$PORT/" || { systemctl status peoplehub-mcp --no-pager | tail -20; exit 1; }

# 8. dominio autorizzato di Firebase Auth (aggiunge, non rimuove; serve la CLI firebase loggata)
PUBLIC_URL="$(sed -n 's/^PUBLIC_URL=//p' "$ETC/env" | tail -1)"
HOST="${PUBLIC_URL#https://}"; HOST="${HOST%%/*}"
if [ "$SKIP_FIREBASE" = 0 ]; then
  node "$MCP/scripts/add-firebase-domain.mjs" "$HOST" || echo "ATTENZIONE: dominio non aggiunto a Firebase Auth (rilancia: node mcp/scripts/add-firebase-domain.mjs $HOST)" >&2
fi

echo "ok. Connettore: $PUBLIC_URL/mcp  Stato: systemctl status peoplehub-mcp  Verifica: bash $DEPLOY/verify.sh"
