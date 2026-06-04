#!/usr/bin/env bash
# Load ci/build.env and export paths. Appends to GITHUB_ENV when set.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="${CI_BUILD_ENV:-$ROOT/ci/build.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "::error::Missing CI config: $ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

WS="${GITHUB_WORKSPACE:-$ROOT}"
EXPORT_ROOT="${WS}/${EXPORT_ROOT_DIR:-export}"
EXPORT_GUIDE="${EXPORT_ROOT}/${GUIDE_SUBDIR:-guide-export}"
RUNNER_HOME="${RUNNER_HOME:-${HOME:-/home/runner}}"

export RUNNER_HOME JAVA_VERSION
export MC_VERSION MC_ASSET_INDEX FORGE_BUILD
export HMC_VERSION MODPACK_DIR MODPACK_REPO
export FGE_VERSION MWE_VERSION
export EXPORT_WARMUP_TICKS EXPORT_WORLD_DELAY_TICKS EXPORT_TIMEOUT_SECONDS
export FIELDGUIDE_EXPORT_MODE
export EXPORT_ROOT EXPORT_GUIDE EXPORT_ROOT_DIR GUIDE_SUBDIR SITE_OUTPUT_DIR RECIPE_BOOK_BASE_URL

if [[ -n "${GITHUB_ENV:-}" ]]; then
  {
    printf 'RUNNER_HOME=%s\n' "$RUNNER_HOME"
    printf 'JAVA_VERSION=%s\n' "$JAVA_VERSION"
    printf 'MC_VERSION=%s\n' "$MC_VERSION"
    printf 'MC_ASSET_INDEX=%s\n' "$MC_ASSET_INDEX"
    printf 'FORGE_BUILD=%s\n' "$FORGE_BUILD"
    printf 'HMC_VERSION=%s\n' "$HMC_VERSION"
    printf 'MODPACK_DIR=%s\n' "$MODPACK_DIR"
    printf 'MODPACK_REPO=%s\n' "$MODPACK_REPO"
    printf 'FGE_VERSION=%s\n' "$FGE_VERSION"
    printf 'MWE_VERSION=%s\n' "$MWE_VERSION"
    printf 'EXPORT_WARMUP_TICKS=%s\n' "$EXPORT_WARMUP_TICKS"
    printf 'EXPORT_WORLD_DELAY_TICKS=%s\n' "$EXPORT_WORLD_DELAY_TICKS"
    printf 'EXPORT_TIMEOUT_SECONDS=%s\n' "$EXPORT_TIMEOUT_SECONDS"
    printf 'FIELDGUIDE_EXPORT_MODE=%s\n' "$FIELDGUIDE_EXPORT_MODE"
    printf 'EXPORT_ROOT_DIR=%s\n' "${EXPORT_ROOT_DIR:-export}"
    printf 'GUIDE_SUBDIR=%s\n' "${GUIDE_SUBDIR:-guide-export}"
    printf 'EXPORT_ROOT=%s\n' "$EXPORT_ROOT"
    printf 'EXPORT_GUIDE=%s\n' "$EXPORT_GUIDE"
    printf 'SITE_OUTPUT_DIR=%s\n' "${SITE_OUTPUT_DIR:-output}"
    printf 'RECIPE_BOOK_BASE_URL=%s\n' "${RECIPE_BOOK_BASE_URL:-}"
  } >> "$GITHUB_ENV"
fi
