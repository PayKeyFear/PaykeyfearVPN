#!/usr/bin/env bash
# Generate a release keystore for PaykeyfearVPN. Run once per signing
# identity. The resulting .jks + passwords must NEVER be committed;
# keep them in a password manager or CI secret store.
#
# Usage:
#   ./scripts/generate-keystore.sh
#
# Outputs (all ignored by .gitignore):
#   release.jks            — the keystore (binary)
#   keystore.properties    — Gradle reads this at build time
#   keystore.secrets.txt   — flat file you can paste into GitHub Secrets
#                            and then delete. DO NOT commit.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -f release.jks ]]; then
    echo "release.jks already exists — refusing to overwrite." >&2
    echo "Move or delete it first if you really want a new keystore." >&2
    exit 1
fi

command -v keytool >/dev/null || {
    echo "keytool not found. Install a JDK 17+ and ensure it's on PATH." >&2
    exit 1
}

# Generate random 32-char passwords (URL-safe base64, no padding).
gen_pw() { openssl rand -base64 24 | tr -d '=+/' | head -c 32; }

STORE_PASS="$(gen_pw)"
KEY_PASS="$(gen_pw)"
KEY_ALIAS="paykeyfear"

# DNAME — all fields inherit from a stable org identity. If you fork this
# project, change at minimum the CN and O.
DNAME="CN=PaykeyfearVPN, O=Paykeyfear, L=Unknown, ST=Unknown, C=RU"

keytool -genkeypair \
    -v \
    -keystore release.jks \
    -storetype PKCS12 \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" \
    -dname "$DNAME"

cat > keystore.properties <<EOF
storeFile=release.jks
storePassword=$STORE_PASS
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PASS
EOF

# Write a one-shot secrets summary the user can copy into GitHub Secrets
# and then shred. We encode the keystore itself for the CI workflow.
KEYSTORE_B64="$(base64 -w0 release.jks 2>/dev/null || base64 release.jks | tr -d '\n')"

cat > keystore.secrets.txt <<EOF
# GitHub → repo → Settings → Secrets and variables → Actions → New repository secret
# Paste each KEY/VALUE pair, then DELETE this file.
# These names match .github/workflows/release.yml.

RELEASE_KEYSTORE_BASE64
$KEYSTORE_B64

RELEASE_KEYSTORE_PASSWORD
$STORE_PASS

RELEASE_KEY_ALIAS
$KEY_ALIAS

RELEASE_KEY_PASSWORD
$KEY_PASS
EOF

echo
echo "✓ Created release.jks, keystore.properties, keystore.secrets.txt"
echo "  - Local release builds will now sign automatically."
echo "  - Copy the four secrets from keystore.secrets.txt into GitHub, then"
echo "    shred that file ($ rm -P keystore.secrets.txt, or Windows equiv.)."
echo "  - Back up release.jks to a password manager; losing it means you"
echo "    can never publish an update to this Play Store listing."
