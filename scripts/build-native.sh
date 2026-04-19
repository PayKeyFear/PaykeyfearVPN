#!/usr/bin/env bash
# Build the three Go-based protocol backends into .aar files under
# protocols/<name>/libs/. Run once per release; skip modules whose .aar is
# already present unless --force is passed.
#
# Prerequisites:
#   - Go 1.22+
#   - Android NDK (r26+) pointed to by $ANDROID_NDK_HOME
#   - gomobile + gobind on $PATH (installed by this script on first run)
#
# Outputs:
#   protocols/awg/libs/awg.aar
#   protocols/vless/libs/vless.aar    (Xray-core + tun2socks bundled)
#   protocols/hysteria2/libs/hysteria.aar

set -euo pipefail

FORCE=0
[[ "${1:-}" == "--force" ]] && FORCE=1

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VENDOR="$ROOT/vendor"
TARGETS="android/arm64,android/arm,android/amd64"
ANDROIDAPI=26

ensure_gomobile() {
    if ! command -v gomobile >/dev/null 2>&1; then
        echo "Installing gomobile…"
        go install golang.org/x/mobile/cmd/gomobile@latest
        go install golang.org/x/mobile/cmd/gobind@latest
        gomobile init
    fi
}

clone_or_update() {
    local url="$1" dir="$2" ref="${3:-}"
    if [[ -d "$dir/.git" ]]; then
        git -C "$dir" fetch --tags --depth=1 origin "${ref:-HEAD}" || true
    else
        git clone --depth=1 ${ref:+--branch "$ref"} "$url" "$dir"
    fi
}

build_awg() {
    local wrapper="$ROOT/third_party/awg-mobile"
    local out="$ROOT/protocols/awg/libs/awg.aar"
    if [[ -f "$out" && $FORCE -eq 0 ]]; then
        echo "✓ awg.aar present, skipping (use --force to rebuild)"
        return
    fi
    if [[ ! -d "$wrapper" ]]; then
        echo "!! $wrapper is missing — see third_party/awg-mobile/README.md"
        return 1
    fi
    mkdir -p "$(dirname "$out")"
    (cd "$wrapper" && \
        go mod tidy && \
        gomobile bind -target="$TARGETS" -androidapi="$ANDROIDAPI" \
        -o "$out" .)
    echo "✓ built $out"
}

build_vless() {
    local wrapper="$ROOT/third_party/vless-mobile"
    local out="$ROOT/protocols/vless/libs/vless.aar"
    if [[ -f "$out" && $FORCE -eq 0 ]]; then
        echo "✓ vless.aar present, skipping"
        return
    fi
    if [[ ! -d "$wrapper" ]]; then
        echo "!! $wrapper is missing — see third_party/vless-mobile/README.md"
        return 1
    fi
    mkdir -p "$(dirname "$out")"
    (cd "$wrapper" && \
        go mod tidy && \
        gomobile bind -target="$TARGETS" -androidapi="$ANDROIDAPI" \
        -o "$out" .)
    echo "✓ built $out"
}

build_hysteria() {
    local wrapper="$ROOT/third_party/hysteria2-mobile"
    local out="$ROOT/protocols/hysteria2/libs/hysteria.aar"
    if [[ -f "$out" && $FORCE -eq 0 ]]; then
        echo "✓ hysteria.aar present, skipping"
        return
    fi
    if [[ ! -d "$wrapper" ]]; then
        echo "!! $wrapper is missing — see third_party/hysteria2-mobile/README.md"
        return 1
    fi
    mkdir -p "$(dirname "$out")"
    (cd "$wrapper" && \
        go mod tidy && \
        gomobile bind -target="$TARGETS" -androidapi="$ANDROIDAPI" \
        -o "$out" ./)
    echo "✓ built $out"
}

main() {
    : "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to your NDK path}"
    ensure_gomobile
    mkdir -p "$VENDOR"
    build_awg
    build_vless
    build_hysteria
}

main "$@"
