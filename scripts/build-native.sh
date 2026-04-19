#!/usr/bin/env bash
# Build the gomobile umbrella .aar containing all three protocol
# wrappers (awg + vless + hysteria2). Single-output design — see
# third_party/gomobile-bundle/bundle.go for why.
#
# The .aar lands in a SINGLE location (protocols/awg/libs/) so AGP sees
# exactly one copy of libgojni.so and of the go.Seq/go.Universe runtime
# classes. vless and hysteria2 modules find their Java class via
# reflection (Class.forName); the lookup succeeds as long as the
# umbrella .aar is anywhere on the app's classpath, which it is
# transitively through :protocols:awg.
#
# Prerequisites:
#   - Go 1.22+ (x/mobile auto-promotes to go1.25)
#   - Android NDK (r26+) pointed to by $ANDROID_NDK_HOME
#   - gomobile + gobind on $PATH (installed by this script on first run)

set -euo pipefail

FORCE=0
[[ "${1:-}" == "--force" ]] && FORCE=1

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
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

build_bundle() {
    local wrapper="$ROOT/third_party/gomobile-bundle"
    local out_dir="$ROOT/protocols/awg/libs"
    local out="$out_dir/paykeyfearnative.aar"

    if [[ -f "$out" && $FORCE -eq 0 ]]; then
        echo "✓ paykeyfearnative.aar present, skipping (use --force to rebuild)"
        return
    fi
    if [[ ! -d "$wrapper" ]]; then
        echo "!! $wrapper is missing — see third_party/gomobile-bundle/"
        return 1
    fi

    # Tidy each sub-module first so their go.sum files exist when the
    # umbrella replaces them in.
    for sub in awg-mobile vless-mobile hysteria2-mobile; do
        if [[ -d "$ROOT/third_party/$sub" ]]; then
            ( cd "$ROOT/third_party/$sub" && go mod tidy ) || true
        fi
    done

    mkdir -p "$out_dir"
    ( cd "$wrapper" \
        && go mod tidy \
        && gomobile bind \
            -target="$TARGETS" \
            -androidapi="$ANDROIDAPI" \
            -o "$out" \
            . )

    # Clean up any stale per-wrapper .aars from previous runs of the
    # old multi-.aar layout — they'd collide with the umbrella.
    rm -f \
        "$ROOT/protocols/awg/libs/awg.aar" \
        "$ROOT/protocols/vless/libs/vless.aar" \
        "$ROOT/protocols/hysteria2/libs/hysteria.aar"

    echo "✓ built $out"
}

main() {
    : "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to your NDK path}"
    ensure_gomobile
    build_bundle
}

main "$@"
