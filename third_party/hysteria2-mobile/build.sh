#!/usr/bin/env bash
# Build hy2mobile into an .aar consumable by :protocols:hysteria2.
#
# Prereqs:
#   - Go 1.22+
#   - $ANDROID_NDK_HOME pointing to NDK r26+
#   - gomobile + gobind on $PATH (run `gomobile init` once)
set -euo pipefail

: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to your NDK path}"

DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="$DIR/../../protocols/hysteria2/libs/hysteria.aar"
mkdir -p "$(dirname "$OUT")"

cd "$DIR"
go mod tidy
gomobile bind \
    -target=android/arm64,android/arm,android/amd64 \
    -androidapi=26 \
    -o "$OUT" \
    .

echo "✓ built $OUT"
