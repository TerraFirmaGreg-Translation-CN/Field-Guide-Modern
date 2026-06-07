#!/usr/bin/env bash
# Field-Guide-Modern CI entrypoint. Usage: bash scripts/ci.sh <command>
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib.sh"

usage() {
  cat <<'EOF'
Usage: bash scripts/ci.sh <command>

Workflow composites:
  prepare-export      env + modpack checkout + bundle id + resolve FGE/MWE tags
  prepare-game        xvfb deps + FGE/MWE jars + HeadlessMC
  finalize-export     export-meta + tar (needs BUNDLE_ID, MODPACK_TAG)
  prepare-deploy      env + resolve bundle id
  install-bundle      extract or fetch (ACQUIRE=extract|fetch, BUNDLE_ID)

Granular (local debugging):
  env, print-versions, checkout-modpack, prepare-bundle-id, export-languages,
  install-mods, setup-hmc, launch-export, write-export-meta,
  resolve-bundle-id, extract-bundle, fetch-bundle, build-site
EOF
}

cmd="${1:-}"
if [[ -z "$cmd" ]]; then
  usage >&2
  exit 1
fi
shift

case "$cmd" in
  env) load_config "$@" ;;
  print-versions) print_versions "$@" ;;
  prepare-export) prepare_export "$@" ;;
  prepare-game) prepare_game "$@" ;;
  finalize-export) finalize_export "$@" ;;
  prepare-deploy) prepare_deploy "$@" ;;
  install-bundle) install_bundle "$@" ;;
  checkout-modpack) checkout_modpack "$@" ;;
  prepare-bundle-id) prepare_bundle_id "$@" ;;
  export-languages) export_languages "$@" ;;
  install-mods) install_export_mods "$@" ;;
  setup-hmc) setup_hmc "$@" ;;
  launch-export) launch_export "$@" ;;
  write-export-meta) write_export_meta "$@" ;;
  resolve-bundle-id) resolve_bundle_id "$@" ;;
  extract-bundle) extract_bundle "$@" ;;
  fetch-bundle) fetch_bundle "$@" ;;
  build-site) build_site "$@" ;;
  -h|--help|help) usage ;;
  *)
    echo "::error::Unknown command: $cmd" >&2
    usage >&2
    exit 1
    ;;
esac
