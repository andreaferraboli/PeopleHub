#!/usr/bin/env bash
# PeopleHub - publish a signed release to GitHub Releases (Linux/macOS).
#
# Bumps the app version, builds the signed release APK locally, pushes the version-bump commit,
# and creates a GitHub Release with the APK attached. The in-app updater then offers it to every
# device. GitHub-only approach (no Firestore): the Release is the single source of truth.
#
# This is the POSIX/Bash counterpart of publish-update.ps1, intended for headless deploys
# (e.g. a Claude agent running on Debian). It is behaviourally identical to the PowerShell script.
#
# Usage:   ./publish-update.sh --version 1.1.1
#          ./publish-update.sh --version 1.1.1 --auto-confirm
#          VERSION=1.1.1 AUTO_CONFIRM=1 ./publish-update.sh
#
# JAVA_HOME: the build needs a JDK 17-21. If JAVA_HOME is already exported it is used as-is;
# otherwise the script falls back to whatever `java` is on PATH. On Debian, install e.g.
# `sudo apt-get install openjdk-17-jdk` or point JAVA_HOME at the Android Studio JBR.
#
# SIGNING (the APK MUST be signed with the SAME key as previous releases, or the in-app updater
# cannot install it over an existing install). The signing key is a secret and is NEVER stored in
# this repository. The script resolves the key, in order:
#   1. A local, git-ignored keystore.properties (+ the .jks it points to) — the developer-laptop path.
#   2. KEYSTORE_BASE64 env var — base64 of the .jks (e.g. `base64 -w0 peoplehub-release.jks`). The
#      Debian agent stores this and the passwords below as secrets in its environment; the script
#      decodes it to a git-ignored file at build time and removes it afterwards. THIS is the path to
#      use for a headless Debian deploy agent. Required companions: KEYSTORE_PASSWORD, KEY_ALIAS,
#      KEY_PASSWORD (and optionally KEY_PASSWORD defaults to KEYSTORE_PASSWORD if unset).
#   3. KEYSTORE_FILE env var — a path to a .jks already present on the machine, with the same
#      KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD companions (the CI path).
# If none resolve, the build would be UNSIGNED and the script aborts (override with --allow-unsigned
# only for throwaway test builds that will never be published to users).

set -euo pipefail

OWNER="andreaferraboli"
REPO="PeopleHub"
BUILD_FILE="app/build.gradle.kts"

VERSION="${VERSION:-}"
AUTO_CONFIRM="${AUTO_CONFIRM:-}"
ALLOW_UNSIGNED="${ALLOW_UNSIGNED:-}"

# --- Argument parsing -------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        -v|--version)
            VERSION="${2:-}"
            shift 2
            ;;
        -y|--auto-confirm)
            AUTO_CONFIRM=1
            shift
            ;;
        --allow-unsigned)
            ALLOW_UNSIGNED=1
            shift
            ;;
        -h|--help)
            grep '^#' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "ERROR: unknown argument: $1" >&2
            exit 1
            ;;
    esac
done

# Run from the script's own directory so relative paths resolve regardless of CWD.
cd "$(dirname "$0")"

echo "=== PeopleHub - Publish Release ==="

# --- 1. Dependencies --------------------------------------------------------
if ! command -v gh >/dev/null 2>&1; then
    echo "ERROR: GitHub CLI (gh) not found. Install: sudo apt-get install gh" >&2
    exit 1
fi
if ! gh auth status >/dev/null 2>&1; then
    echo "ERROR: not authenticated. Run: gh auth login  (or set GH_TOKEN)" >&2
    exit 1
fi

# --- 2. Current version -----------------------------------------------------
if [[ ! -f "$BUILD_FILE" ]]; then
    echo "ERROR: $BUILD_FILE not found (run from the repo root)" >&2
    exit 1
fi
currentVersion="$(sed -n 's/.*val appVersionName = "\([0-9]\+\.[0-9]\+\.[0-9]\+\)".*/\1/p' "$BUILD_FILE" | head -n1)"
if [[ -z "$currentVersion" ]]; then
    echo "ERROR: could not find appVersionName in $BUILD_FILE" >&2
    exit 1
fi
echo "Current version: $currentVersion"

# --- 3. New version ---------------------------------------------------------
if [[ -z "$VERSION" ]]; then
    read -rp "New version X.Y.Z: " VERSION
fi
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "ERROR: invalid version. Use X.Y.Z, for example 1.1.1" >&2
    exit 1
fi

# --- 4. Bump appVersionName -------------------------------------------------
if [[ "$VERSION" != "$currentVersion" ]]; then
    echo "Bumping appVersionName to $VERSION"
    sed -i "s/val appVersionName = \"[0-9]\+\.[0-9]\+\.[0-9]\+\"/val appVersionName = \"$VERSION\"/" "$BUILD_FILE"
fi

# --- 4b. Resolve signing key (see SIGNING note in the header) ---------------
# build.gradle.kts reads keystore.properties first, then the KEYSTORE_FILE env var. We pick a path
# here and, when needed, decode KEYSTORE_BASE64 to a git-ignored file so the Gradle build can sign.
DECODED_KEYSTORE=""
cleanup_keystore() { [[ -n "$DECODED_KEYSTORE" && -f "$DECODED_KEYSTORE" ]] && rm -f "$DECODED_KEYSTORE"; }
trap cleanup_keystore EXIT

if [[ -f "keystore.properties" ]]; then
    echo "Signing: using local keystore.properties"
elif [[ -n "${KEYSTORE_BASE64:-}" ]]; then
    echo "Signing: decoding KEYSTORE_BASE64 into a temporary git-ignored keystore"
    mkdir -p keystore
    DECODED_KEYSTORE="keystore/peoplehub-release.jks"   # matches .gitignore (*.jks), never committed
    printf '%s' "$KEYSTORE_BASE64" | base64 -d > "$DECODED_KEYSTORE"
    export KEYSTORE_FILE="$DECODED_KEYSTORE"
    export KEY_PASSWORD="${KEY_PASSWORD:-${KEYSTORE_PASSWORD:-}}"
    if [[ -z "${KEYSTORE_PASSWORD:-}" || -z "${KEY_ALIAS:-}" || -z "${KEY_PASSWORD:-}" ]]; then
        echo "ERROR: KEYSTORE_BASE64 set but KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD missing" >&2
        exit 1
    fi
elif [[ -n "${KEYSTORE_FILE:-}" ]]; then
    echo "Signing: using KEYSTORE_FILE=$KEYSTORE_FILE"
    if [[ -z "${KEYSTORE_PASSWORD:-}" || -z "${KEY_ALIAS:-}" || -z "${KEY_PASSWORD:-}" ]]; then
        echo "ERROR: KEYSTORE_FILE set but KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD missing" >&2
        exit 1
    fi
elif [[ -n "$ALLOW_UNSIGNED" ]]; then
    echo "WARNING: no signing key found; building an UNSIGNED APK (--allow-unsigned). Do NOT publish this to users."
else
    echo "ERROR: no signing key available — the release APK would be UNSIGNED and unusable as an update." >&2
    echo "       Provide one of: a local keystore.properties, KEYSTORE_BASE64 (+ passwords), or KEYSTORE_FILE (+ passwords)." >&2
    echo "       See the SIGNING note at the top of this script. Use --allow-unsigned only for throwaway test builds." >&2
    exit 1
fi

if [[ -z "$AUTO_CONFIRM" ]]; then
    read -rp "Build and publish v$VERSION now? y/n: " confirm
    if [[ "$confirm" != "y" ]]; then
        echo "Cancelled"
        exit 0
    fi
fi

# --- 5. Build signed release APK --------------------------------------------
echo "STEP 1/4: Building signed release APK..."
# Use JAVA_HOME if already set; otherwise derive it from `java` on PATH.
if [[ -z "${JAVA_HOME:-}" ]] && command -v java >/dev/null 2>&1; then
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
    export JAVA_HOME
fi
chmod +x ./gradlew
./gradlew :app:assembleRelease

apkPath="app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$apkPath" ]]; then
    echo "ERROR: APK not found at $apkPath" >&2
    exit 1
fi
releaseApk="PeopleHub-$VERSION.apk"
cp -f "$apkPath" "$releaseApk"
echo "   OK: $releaseApk"

# Guard against accidentally publishing an unsigned APK (the updater could not install it).
if [[ -z "$ALLOW_UNSIGNED" ]]; then
    apksigner="$(command -v apksigner || true)"
    if [[ -z "$apksigner" && -n "${ANDROID_HOME:-}" ]]; then
        apksigner="$(ls "$ANDROID_HOME"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n1 || true)"
    fi
    if [[ -n "$apksigner" ]]; then
        if ! "$apksigner" verify "$releaseApk" >/dev/null 2>&1; then
            echo "ERROR: $releaseApk is not signed — refusing to publish. Check the signing config." >&2
            exit 1
        fi
        echo "   Signature verified."
    else
        echo "   NOTE: apksigner not found; skipping signature verification (install Android build-tools to enable it)."
    fi
fi

# --- 6. Commit the version bump and push ------------------------------------
echo "STEP 2/4: Committing version bump..."
git add "$BUILD_FILE"
if ! git diff --cached --quiet; then
    git commit -m "Release v$VERSION"
fi
git push origin HEAD

# --- 7. Create the GitHub Release with the APK attached ---------------------
echo "STEP 3/4: Creating GitHub Release v$VERSION..."
tag="v$VERSION"
gh release create "$tag" "$releaseApk" --repo "$OWNER/$REPO" --title "$tag" --generate-notes

echo "STEP 4/4: Done."
echo ""
echo "Published v$VERSION"
echo "Release: https://github.com/$OWNER/$REPO/releases/tag/$tag"
echo "Devices get the in-app update on next launch, or via Vault, Check for updates."
