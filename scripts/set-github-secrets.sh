#!/usr/bin/env bash
# Uploads the APK signing key and Supabase settings to this repo's GitHub Actions secrets.
# Run from the repo root in Git Bash:  bash scripts/set-github-secrets.sh
# Nothing is printed; values are read from files outside the repo.
set -euo pipefail

REPO="kylediaslobo/ReceiptScanner"
SIGNING_DIR="$HOME/.receipt-scanner-signing"
PROPS="local.properties"

prop() { grep "^$1=" "$2" | head -n1 | cut -d= -f2- | tr -d '\r\n'; }

if [ -f "$SIGNING_DIR/release.jks" ]; then
  base64 -w0 "$SIGNING_DIR/release.jks" | gh secret set RELEASE_KEYSTORE_BASE64 -R "$REPO"
  prop RELEASE_KEYSTORE_PASSWORD "$SIGNING_DIR/signing.properties" | gh secret set RELEASE_KEYSTORE_PASSWORD -R "$REPO"
  prop RELEASE_KEY_PASSWORD "$SIGNING_DIR/signing.properties" | gh secret set RELEASE_KEY_PASSWORD -R "$REPO"
  prop RELEASE_KEY_ALIAS "$SIGNING_DIR/signing.properties" | gh secret set RELEASE_KEY_ALIAS -R "$REPO"
  echo "Signing secrets set."
else
  echo "No keystore at $SIGNING_DIR/release.jks; skipping signing secrets." >&2
fi

if [ -f "$PROPS" ] && [ -n "$(prop SUPABASE_URL "$PROPS")" ]; then
  prop SUPABASE_URL "$PROPS" | gh secret set SUPABASE_URL -R "$REPO"
  prop SUPABASE_ANON_KEY "$PROPS" | gh secret set SUPABASE_ANON_KEY -R "$REPO"
  echo "Supabase secrets set."
else
  echo "Fill in SUPABASE_URL and SUPABASE_ANON_KEY in local.properties first, then run this again." >&2
fi

gh secret list -R "$REPO"
