#!/usr/bin/env bash
# Builds vless.aar (Xray-core + tun2socks) for Android. Invoked by
# scripts/build-native.sh; can also be run standalone for debugging.
set -euo pipefail

cd "$(dirname "$0")"

: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME}"
TARGETS="${TARGETS:-android/arm64,android/arm,android/amd64}"
ANDROIDAPI="${ANDROIDAPI:-26}"
OUT="${OUT:-../../protocols/vless/libs/vless.aar}"

go mod tidy
mkdir -p "$(dirname "$OUT")"
gomobile bind \
    -target="$TARGETS" \
    -androidapi="$ANDROIDAPI" \
    -o "$OUT" \
    .
echo "✓ built $OUT"
