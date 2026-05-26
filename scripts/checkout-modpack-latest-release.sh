#!/usr/bin/env bash
# Check out the latest Modpack-Modern *release* tag (semver x.y.z), not the dev branch.
# Override: MODPACK_TAG=0.12.7 ./scripts/checkout-modpack-latest-release.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MP="${MODPACK_DIR:-$ROOT/Modpack-Modern}"

cd "$ROOT"
git submodule update --init --recursive Modpack-Modern 2>/dev/null || true

if [[ ! -e "$MP/.git" ]]; then
  echo "error: Modpack-Modern submodule missing; run from Field-Guide-Modern root" >&2
  exit 1
fi

cd "$MP"
git fetch --tags --force origin

if [[ -n "${MODPACK_TAG:-}" ]]; then
  TAG="$MODPACK_TAG"
  echo "Using MODPACK_TAG override: $TAG"
else
  TAG="$(git tag -l | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1)"
  if [[ -z "$TAG" ]]; then
    echo "error: no semver release tags found" >&2
    exit 1
  fi
  echo "Latest release tag: $TAG"
fi

git checkout "$TAG"
git describe --tags --exact-match 2>/dev/null || git describe --tags --always

# For GitHub Actions
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  echo "modpack_tag=$TAG" >> "$GITHUB_OUTPUT"
fi
