#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bundle_id="${BUNDLE_ID:?BUNDLE_ID required}"
modpack_tag="${MODPACK_TAG:?MODPACK_TAG required}"
out="$ROOT/export-meta"

mkdir -p "$out"
printf '%s\n' "$bundle_id" > "$out/bundle-id"
printf '%s\n' "$modpack_tag" > "$out/modpack-tag"
echo "Wrote export-meta (bundle_id=$bundle_id modpack_tag=$modpack_tag)"
