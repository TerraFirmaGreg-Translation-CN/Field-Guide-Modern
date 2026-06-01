#!/usr/bin/env bash
# Extract guide-export-<bundle_id>.tar.gz into $EXPORT_ROOT (guide-export/ + emi/).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT/scripts/ci-load-config.sh"

bundle_id="${BUNDLE_ID:?BUNDLE_ID required}"
archive="$ROOT/guide-export-${bundle_id}.tar.gz"

if [[ ! -f "$archive" ]]; then
  echo "::error::Missing ${archive} after artifact download" >&2
  ls -la "$ROOT" >&2
  exit 1
fi

rm -rf "$EXPORT_ROOT"
mkdir -p "$EXPORT_ROOT"
tar -xzf "$archive" -C "$EXPORT_ROOT"

rm -f "$archive"
bash "$ROOT/scripts/ci-verify-guide-export.sh"
echo "Extracted export bundle to ${EXPORT_ROOT}"
