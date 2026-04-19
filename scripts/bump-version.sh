#!/usr/bin/env bash
# Bump versionCode (+1) and versionName in app/build.gradle.kts.
# Usage:
#   ./scripts/bump-version.sh                 # bumps patch of versionName
#   ./scripts/bump-version.sh 0.2.0           # sets versionName explicitly
#   ./scripts/bump-version.sh --minor         # bumps minor (0.1.2 -> 0.2.0)
#   ./scripts/bump-version.sh --major         # bumps major (0.1.2 -> 1.0.0)

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE="$ROOT/app/build.gradle.kts"

current_code=$(grep -Eo 'versionCode\s*=\s*[0-9]+' "$GRADLE" | grep -Eo '[0-9]+')
current_name=$(grep -Eo 'versionName\s*=\s*"[^"]+"' "$GRADLE" | sed -E 's/.*"([^"]+)".*/\1/')

IFS='.' read -r MAJOR MINOR PATCH <<<"$current_name"

case "${1:-}" in
    --major)
        next_name="$((MAJOR + 1)).0.0"
        ;;
    --minor)
        next_name="${MAJOR}.$((MINOR + 1)).0"
        ;;
    "" | --patch)
        next_name="${MAJOR}.${MINOR}.$((PATCH + 1))"
        ;;
    *)
        next_name="$1"
        ;;
esac

next_code=$((current_code + 1))

echo "versionCode: $current_code -> $next_code"
echo "versionName: $current_name -> $next_name"

# Portable in-place sed (BSD/GNU): use a temp file.
tmp="$(mktemp)"
sed -E \
    -e "s/(versionCode\s*=\s*)[0-9]+/\1$next_code/" \
    -e "s/(versionName\s*=\s*\")[^\"]+(\")/\1$next_name\2/" \
    "$GRADLE" >"$tmp"
mv "$tmp" "$GRADLE"

if command -v git >/dev/null 2>&1 && git -C "$ROOT" rev-parse --git-dir >/dev/null 2>&1; then
    echo "Ready to tag: git commit -am 'Release v$next_name' && git tag v$next_name"
fi
