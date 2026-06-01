#!/usr/bin/env bash
# Download guide-export-<bundle_id> from the latest successful Export handbook bundle run.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT/scripts/ci-load-config.sh"

bundle_id="${BUNDLE_ID:?BUNDLE_ID required}"
artifact_name="guide-export-${bundle_id}"

if [[ -f "${EXPORT_ROOT}/guide-export/manifest.json" && -f "${EXPORT_ROOT}/emi/bundle.json" ]]; then
  echo "Export bundle already at ${EXPORT_ROOT}"
  bash "$ROOT/scripts/ci-verify-guide-export.sh"
  exit 0
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "::error::gh CLI required to download artifact ${artifact_name}" >&2
  exit 1
fi

run_id="$(
  gh run list \
    --repo "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY required}" \
    --workflow "Export handbook bundle" \
    --branch "${GITHUB_REF_NAME:-main}" \
    --status success \
    --limit 1 \
    --json databaseId \
    -q '.[0].databaseId'
)"

if [[ -z "$run_id" ]]; then
  echo "::error::No successful「Export handbook bundle」run on branch ${GITHUB_REF_NAME:-main}" >&2
  exit 1
fi

rm -f "$ROOT/guide-export-${bundle_id}.tar.gz"
gh run download "$run_id" --repo "$GITHUB_REPOSITORY" -n "$artifact_name" -D "$ROOT"
bash "$ROOT/scripts/ci-extract-export-bundle.sh"
echo "Installed export from run ${run_id} (artifact ${artifact_name})"
