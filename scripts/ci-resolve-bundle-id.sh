#!/usr/bin/env bash
# Resolve fg-<modpack_tag> from export-meta, input, or latest Modpack-Modern release tag.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [[ -n "${BUNDLE_ID_INPUT:-}" ]]; then
  id="$BUNDLE_ID_INPUT"
elif [[ -f "$ROOT/export-meta/bundle-id" ]]; then
  id="$(tr -d '\r\n' < "$ROOT/export-meta/bundle-id")"
elif [[ -n "${MODPACK_TAG:-}" ]]; then
  id="fg-${MODPACK_TAG}"
else
  # shellcheck disable=SC1091
  source "$ROOT/scripts/ci-load-config.sh"
  MODPACK_REPO="${MODPACK_REPO:-https://github.com/TerraFirmaGreg-Team/Modpack-Modern.git}"
  MODPACK_TAG="$(
    git ls-remote --tags "$MODPACK_REPO" \
      | awk -F/ '{print $NF}' \
      | sed 's/\^{}//' \
      | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' \
      | sort -Vu \
      | tail -n 1
  )"
  if [[ -z "$MODPACK_TAG" ]]; then
    echo "::error::Could not resolve modpack tag for bundle id" >&2
    exit 1
  fi
  id="fg-${MODPACK_TAG}"
fi

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  echo "bundle_id=${id}" >> "$GITHUB_OUTPUT"
fi
echo "bundle_id=${id}"
