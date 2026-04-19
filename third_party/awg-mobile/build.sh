#!/usr/bin/env bash
# Build awgmobile into an .aar consumable by :protocols:awg.
set -euo pipefail

: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to your NDK path}"

DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="$DIR/../../protocols/awg/libs/awg.aar"
mkdir -p "$(dirname "$OUT")"

cd "$DIR"
go mod tidy
gomobile bind \
    -target=android/arm64,android/arm,android/amd64 \
    -androidapi=26 \
    -o "$OUT" \
    .

echo "✓ built $OUT"
