#!/usr/bin/env bash
# Emit bundle_id=fg-<modpack_tag> (aligned with TFG-Recipe-Viewer tfg-<tag>).
set -euo pipefail

tag="${MODPACK_TAG:?MODPACK_TAG required}"
id="fg-${tag}"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "bundle_id=${id}"
    echo "modpack_tag=${tag}"
  } >> "$GITHUB_OUTPUT"
fi

echo "bundle_id=${id} (modpack @ ${tag})"
