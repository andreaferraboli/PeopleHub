#!/usr/bin/env bash
# Rideploy in un comando: aggiorna il checkout (branch corrente) e rilancia l'installazione idempotente.
# Uso (da root): bash mcp/deploy/update.sh [opzioni di install.sh]
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
git -C "$REPO" pull --ff-only
exec bash "$REPO/mcp/deploy/install.sh" --skip-firebase "$@"
