#!/usr/bin/env bash
# Shared CI helpers for Field-Guide-Modern (sourced by scripts/ci.sh).
set -euo pipefail

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FGM_ROOT="$(cd "$LIB_DIR/.." && pwd)"
# shellcheck source=ci/lib/github-release.sh
source "$LIB_DIR/ci/lib/github-release.sh"

load_config() {
  local env_file="${CI_BUILD_ENV:-$FGM_ROOT/ci/build.env}"
  if [[ ! -f "$env_file" ]]; then
    echo "::error::Missing CI config: $env_file" >&2
    exit 1
  fi

  set -a
  # shellcheck disable=SC1090
  source "$env_file"
  set +a

  local ws="${GITHUB_WORKSPACE:-$FGM_ROOT}"
  EXPORT_ROOT="${ws}/${EXPORT_ROOT_DIR:-export}"
  EXPORT_GUIDE="${EXPORT_ROOT}/${GUIDE_SUBDIR:-guide-export}"
  RUNNER_HOME="${RUNNER_HOME:-${HOME:-/home/runner}}"

  export RUNNER_HOME JAVA_VERSION
  export MC_VERSION MC_ASSET_INDEX FORGE_BUILD
  export HMC_VERSION MODPACK_DIR MODPACK_REPO
  export FGE_REPO FGE_VERSION MWE_REPO MWE_VERSION
  export EXPORT_WARMUP_TICKS EXPORT_WORLD_DELAY_TICKS EXPORT_TIMEOUT_SECONDS
  export EXPORT_ROOT EXPORT_GUIDE EXPORT_ROOT_DIR GUIDE_SUBDIR SITE_OUTPUT_DIR RECIPE_BOOK_BASE_URL
  export EXPORT_ARTIFACT_NAME="${EXPORT_ARTIFACT_NAME:-field-guide}"

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
      printf 'FGE_REPO=%s\n' "${FGE_REPO:-jmecn/field-guide-export}"
      printf 'FGE_VERSION=%s\n' "${FGE_VERSION:-}"
      printf 'MWE_REPO=%s\n' "${MWE_REPO:-jmecn/minecraft-web-export}"
      printf 'MWE_VERSION=%s\n' "${MWE_VERSION:-}"
      printf 'EXPORT_WARMUP_TICKS=%s\n' "$EXPORT_WARMUP_TICKS"
      printf 'EXPORT_WORLD_DELAY_TICKS=%s\n' "$EXPORT_WORLD_DELAY_TICKS"
      printf 'EXPORT_TIMEOUT_SECONDS=%s\n' "$EXPORT_TIMEOUT_SECONDS"
      printf 'EXPORT_ROOT_DIR=%s\n' "${EXPORT_ROOT_DIR:-export}"
      printf 'GUIDE_SUBDIR=%s\n' "${GUIDE_SUBDIR:-guide-export}"
      printf 'EXPORT_ROOT=%s\n' "$EXPORT_ROOT"
      printf 'EXPORT_GUIDE=%s\n' "$EXPORT_GUIDE"
      printf 'SITE_OUTPUT_DIR=%s\n' "${SITE_OUTPUT_DIR:-output}"
      printf 'RECIPE_BOOK_BASE_URL=%s\n' "${RECIPE_BOOK_BASE_URL:-}"
      printf 'EXPORT_ARTIFACT_NAME=%s\n' "${EXPORT_ARTIFACT_NAME:-field-guide}"
    } >> "$GITHUB_ENV"
  fi
}

resolve_latest_modpack_tag() {
  resolve_modpack_tag
}

print_versions() {
  load_config

  if [[ -z "${MODPACK_TAG:-}" ]]; then
    unset MODPACK_TAG
  fi

  local modpack fge mwe
  modpack="${MODPACK_TAG:-$(resolve_modpack_tag)}"
  if [[ -z "$modpack" ]]; then
    echo "::error::Could not resolve Modpack-Modern release tag" >&2
    exit 1
  fi

  fge="$(resolve_fge_tag)" || exit 1
  mwe="$(resolve_mwe_tag)" || exit 1

  export MODPACK_TAG="$modpack"
  export FGE_TAG="$fge"
  export MWE_TAG="$mwe"

  if [[ -n "${GITHUB_ENV:-}" ]]; then
    {
      printf 'MODPACK_TAG=%s\n' "$modpack"
      printf 'FGE_TAG=%s\n' "$fge"
      printf 'MWE_TAG=%s\n' "$mwe"
      printf 'FGE_VERSION=%s\n' "$fge"
      printf 'MWE_VERSION=%s\n' "$mwe"
    } >> "$GITHUB_ENV"
  fi

  echo "::group::CI resolved versions"
  printf '%s\n' \
    "modpack_tag=${modpack}" \
    "field-guide-export=${fge}" \
    "minecraft-web-export=${mwe}" \
    "minecraft=${MC_VERSION} (assets ${MC_ASSET_INDEX})" \
    "forge_build=${FORGE_BUILD}" \
    "headlessmc=${HMC_VERSION}"
  echo "::endgroup::"

  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    {
      echo "## Resolved versions"
      echo ""
      echo "| Component | Version |"
      echo "|-----------|---------|"
      echo "| Modpack-Modern | \`${modpack}\` |"
      echo "| field-guide-export | \`${fge}\` |"
      echo "| minecraft-web-export | \`${mwe}\` |"
      echo "| Minecraft / Forge | \`${MC_VERSION}\` / \`${FORGE_BUILD}\` |"
      echo "| HeadlessMC | \`${HMC_VERSION}\` |"
    } >> "$GITHUB_STEP_SUMMARY"
  fi
}

checkout_modpack() {
  local mp="${MODPACK_DIR:-$FGM_ROOT/Modpack-Modern}"
  local repo="${MODPACK_REPO:-https://github.com/TerraFirmaGreg-Team/Modpack-Modern.git}"
  local tag

  if [[ -n "${MODPACK_TAG:-}" ]]; then
    tag="$MODPACK_TAG"
    echo "Using MODPACK_TAG override: $tag"
  else
    tag="$(resolve_modpack_tag)"
    if [[ -z "$tag" ]]; then
      echo "::error::No semver release tags found on ${MODPACK_REPO:-Modpack-Modern}" >&2
      exit 1
    fi
    echo "Latest release tag: $tag"
  fi

  cd "$FGM_ROOT"
  if [[ -e "$mp/.git" ]]; then
    local current
    current="$(git -C "$mp" describe --tags --exact-match 2>/dev/null || true)"
    if [[ "$current" == "$tag" ]]; then
      echo "Modpack-Modern already at $tag"
    else
      echo "Replacing $mp (was ${current:-unknown}) with shallow clone @ $tag ..."
      rm -rf "$mp"
      git clone --depth 1 --branch "$tag" "$repo" "$mp"
    fi
  else
    echo "Shallow cloning Modpack-Modern @ $tag into $mp ..."
    git clone --depth 1 --branch "$tag" "$repo" "$mp"
  fi

  cd "$mp"
  git describe --tags --exact-match 2>/dev/null || git describe --tags --always

  export MODPACK_TAG="$tag"
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    echo "modpack_tag=$tag" >> "$GITHUB_OUTPUT"
  fi
}

prepare_export() {
  load_config
  checkout_modpack
  prepare_bundle_id
  print_versions
  echo "Modpack-Modern @ ${MODPACK_TAG} → bundle_id=fg-${MODPACK_TAG}"
}

prepare_bundle_id() {
  local tag="${MODPACK_TAG:?MODPACK_TAG required}"
  local id="fg-${tag}"

  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    {
      echo "bundle_id=${id}"
      echo "modpack_tag=${tag}"
    } >> "$GITHUB_OUTPUT"
  fi
  echo "bundle_id=${id} (modpack @ ${tag})"
}

export_languages() {
  cd "$FGM_ROOT"
  chmod +x gradlew

  local lang_file="$FGM_ROOT/build/export-languages.txt"
  ./gradlew writeExportLanguagesFile --no-daemon -q --console=plain >/dev/null

  if [[ ! -s "$lang_file" ]]; then
    echo "::error::Missing $lang_file after writeExportLanguagesFile" >&2
    exit 1
  fi

  local csv
  csv="$(tr -d '\n\r' < "$lang_file")"

  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    {
      echo "export_languages<<EOF"
      echo "$csv"
      echo "EOF"
    } >> "$GITHUB_OUTPUT"
  fi
  echo "Export languages (Language enum): ${csv}"
}

install_gh_release_jar() {
  local repo=$1 tag=$2 jar_prefix=$3
  shift 3
  local extra_patterns=("$@")

  local ver="${tag#v}"
  local jar_name="${jar_prefix}-${ver}.jar"
  local mp="${MODPACK_DIR:-$FGM_ROOT/Modpack-Modern}"

  cd "$FGM_ROOT"
  rm -f "${jar_prefix}-"*.jar
  gh release download "$tag" --repo "$repo" --pattern "$jar_name" --clobber

  mkdir -p "$mp/mods"
  find "$mp/mods" -maxdepth 1 -name "${jar_prefix}*.jar" -delete
  for pat in "${extra_patterns[@]}"; do
    find "$mp/mods" -maxdepth 1 -name "$pat" -delete
  done

  local jar
  jar=$(ls "${jar_prefix}-"*.jar | head -1)
  if [[ -z "$jar" ]]; then
    echo "::error::No ${jar_prefix} jar from ${repo}@${tag}" >&2
    exit 1
  fi
  cp -v "$jar" "$mp/mods/"
}

install_export_mods() {
  local fge_tag mwe_tag
  fge_tag="$(resolve_fge_tag)" || exit 1
  mwe_tag="$(resolve_mwe_tag)" || exit 1
  echo "Installing field-guide-export ${fge_tag}, minecraft-web-export ${mwe_tag}"

  install_gh_release_jar "${FGE_REPO:-jmecn/field-guide-export}" "$fge_tag" field-guide-export \
    'field-guide-forge*.jar' 'fieldguide*.jar'
  install_gh_release_jar "${MWE_REPO:-jmecn/minecraft-web-export}" "$mwe_tag" minecraft-web-export
}

install_display_deps() {
  if command -v xvfb-run >/dev/null 2>&1; then
    return 0
  fi
  sudo DEBIAN_FRONTEND=noninteractive apt-get update
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
    xvfb x11-xserver-utils \
    libgl1 libgl1-mesa-dri \
    libopenal1
}

prepare_game() {
  install_display_deps
  install_export_mods
  setup_hmc
}

setup_hmc() {
  local hmc_ver="${HMC_VERSION:?HMC_VERSION required}"
  local mc_ver="${MC_VERSION:?MC_VERSION required}"
  local forge="${FORGE_BUILD:?FORGE_BUILD required}"
  local mp="${MODPACK_DIR:-Modpack-Modern}"
  local mp_abs
  mp_abs="$(cd "$FGM_ROOT/$mp" && pwd)"
  local launcher="headlessmc-launcher-${hmc_ver}.jar"

  cd "$FGM_ROOT"
  if [[ ! -f "$launcher" ]]; then
    gh release download "$hmc_ver" \
      --repo 3arthqu4ke/headlessmc \
      --pattern "$launcher" \
      --clobber
  fi

  mkdir -p HeadlessMC
  cat > HeadlessMC/config.properties <<EOF
hmc.java.versions=$JAVA_HOME/bin/java
hmc.gamedir=$mp_abs
hmc.offline=true
hmc.rethrow.launch.exceptions=true
hmc.exit.on.failed.command=true
EOF

  if [[ ! -f "$HOME/.minecraft/versions/$mc_ver/$mc_ver.json" ]]; then
    java -jar "$launcher" --command "download $mc_ver"
  fi
  if ! ls "$HOME/.minecraft/versions" 2>/dev/null | grep -q "$forge"; then
    java -jar "$launcher" --command "forge $mc_ver --uid $forge"
  fi
}

verify_guide_export() {
  local guide="${EXPORT_GUIDE:?EXPORT_GUIDE required}"
  local root="${EXPORT_ROOT:?EXPORT_ROOT required}"

  for f in manifest.json meta.json; do
    if [[ ! -f "$guide/$f" ]]; then
      echo "::error::Missing $guide/$f"
      exit 1
    fi
  done

  local exporter
  exporter=$(python3 -c "import json; print(json.load(open('$guide/manifest.json')).get('exporter',''))")
  if [[ "$exporter" != "field-guide-export" ]]; then
    echo "::error::manifest.exporter must be field-guide-export (got: $exporter)"
    exit 1
  fi

  for d in assets data lang assets/icons; do
    if [[ ! -d "$guide/$d" ]]; then
      echo "::error::Missing directory $guide/$d"
      exit 1
    fi
  done

  if [[ -d "$guide/emi" ]]; then
    echo "::error::guide-export must not contain emi/ (use $root/emi)"
    exit 1
  fi

  if [[ ! -d "$root/emi" ]]; then
    echo "::error::Missing EMI bundle at $root/emi"
    exit 1
  fi

  if [[ ! -f "$root/emi/bundle.json" ]]; then
    echo "::error::Missing $root/emi/bundle.json"
    exit 1
  fi

  local schema
  schema=$(python3 -c "import json; print(json.load(open('$root/emi/bundle.json')).get('schema',0))")
  if [[ "$schema" != "2" ]]; then
    echo "::error::emi/bundle.json schema must be 2 (got: $schema)"
    exit 1
  fi

  echo "guide-export OK: $guide"
  du -sh "$guide" "$guide/assets" "$guide/data" "$guide/lang" "$guide/assets/icons" 2>/dev/null || true
  du -sh "${root}/emi" 2>/dev/null || true
}

launch_export() {
  local mp="${MODPACK_DIR:-$FGM_ROOT/Modpack-Modern}"
  local root="${EXPORT_ROOT:?EXPORT_ROOT required}"
  local hmc_ver="${HMC_VERSION:?HMC_VERSION required}"
  local launcher="headlessmc-launcher-${hmc_ver}.jar"

  mkdir -p "$mp/config" "$mp/saves" "$root"
  cp -f "$FGM_ROOT/ci/config/export-fml.toml" "$mp/config/fml.toml"
  cp -f "$FGM_ROOT/ci/config/export-forge-client.toml" "$mp/config/forge-client.toml"
  cat > "$mp/options.txt" <<EOF
onboardAccessibility:false
pauseOnLostFocus:false
EOF

  cd "$FGM_ROOT"
  xvfb-run --server-args="-screen 0 1280x720x24" -a java \
    -Dhmc.check.xvfb=true \
    -jar "$launcher" \
    --command "launch .*forge.* -regex --jvm \"${MWE_JVM_FLAGS:?MWE_JVM_FLAGS required}\""

  verify_guide_export
}

write_export_meta() {
  local bundle_id="${BUNDLE_ID:?BUNDLE_ID required}"
  local modpack_tag="${MODPACK_TAG:?MODPACK_TAG required}"
  local out="$FGM_ROOT/export-meta"

  mkdir -p "$out"
  printf '%s\n' "$bundle_id" > "$out/bundle-id"
  printf '%s\n' "$modpack_tag" > "$out/modpack-tag"
  echo "Wrote export-meta (bundle_id=$bundle_id modpack_tag=$modpack_tag)"
}

finalize_export() {
  write_export_meta
  local bundle_id="${BUNDLE_ID:?BUNDLE_ID required}"
  local archive="$FGM_ROOT/guide-export-${bundle_id}.tar.gz"

  load_config
  tar -czf "$archive" -C "$EXPORT_ROOT" guide-export emi
  ls -lh "$archive"
}

prepare_deploy() {
  load_config
  resolve_bundle_id
}

install_bundle() {
  case "${ACQUIRE:-extract}" in
    extract) extract_bundle ;;
    fetch) fetch_bundle ;;
    *)
      echo "::error::ACQUIRE must be extract or fetch (got: ${ACQUIRE:-})" >&2
      exit 1
      ;;
  esac
}

resolve_bundle_id() {
  local id

  if [[ -n "${BUNDLE_ID_INPUT:-}" ]]; then
    id="$BUNDLE_ID_INPUT"
  elif [[ -f "$FGM_ROOT/export-meta/bundle-id" ]]; then
    id="$(tr -d '\r\n' < "$FGM_ROOT/export-meta/bundle-id")"
  elif [[ -n "${MODPACK_TAG:-}" ]]; then
    id="fg-${MODPACK_TAG}"
  else
    load_config
    MODPACK_TAG="$(resolve_latest_modpack_tag)"
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
}

extract_bundle() {
  local bundle_id="${BUNDLE_ID:?BUNDLE_ID required}"
  local archive="$FGM_ROOT/guide-export-${bundle_id}.tar.gz"

  load_config

  if [[ ! -f "$archive" ]]; then
    echo "::error::Missing ${archive} after artifact download" >&2
    ls -la "$FGM_ROOT" >&2
    exit 1
  fi

  rm -rf "$EXPORT_ROOT"
  mkdir -p "$EXPORT_ROOT"
  tar -xzf "$archive" -C "$EXPORT_ROOT"
  rm -f "$archive"

  verify_guide_export
  echo "Extracted export bundle to ${EXPORT_ROOT}"
}

fetch_bundle() {
  local bundle_id="${BUNDLE_ID:?BUNDLE_ID required}"

  load_config

  if [[ -f "${EXPORT_ROOT}/guide-export/manifest.json" && -f "${EXPORT_ROOT}/emi/bundle.json" ]]; then
    echo "Export bundle already at ${EXPORT_ROOT}"
    verify_guide_export
    return 0
  fi

  if ! command -v gh >/dev/null 2>&1; then
    echo "::error::gh CLI required to download artifact ${EXPORT_ARTIFACT_NAME}" >&2
    exit 1
  fi

  local artifact_name="${EXPORT_ARTIFACT_NAME:-field-guide}"
  local workflow_name="${EXPORT_WORKFLOW_NAME:-Export field guide}"

  local run_id
  run_id="$(
    gh run list \
      --repo "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY required}" \
      --workflow "$workflow_name" \
      --branch "${GITHUB_REF_NAME:-main}" \
      --status success \
      --limit 1 \
      --json databaseId \
      -q '.[0].databaseId'
  )"

  if [[ -z "$run_id" ]]; then
    echo "::error::No successful「${workflow_name}」run on branch ${GITHUB_REF_NAME:-main}" >&2
    exit 1
  fi

  rm -f "$FGM_ROOT/guide-export-${bundle_id}.tar.gz"
  gh run download "$run_id" --repo "$GITHUB_REPOSITORY" -n "$artifact_name" -D "$FGM_ROOT"
  extract_bundle
  echo "Installed export from run ${run_id} (artifact ${artifact_name})"
}

build_site() {
  load_config

  cd "$FGM_ROOT"
  chmod +x gradlew
  ./gradlew jar --no-daemon

  local site_jar
  site_jar=$(ls -t build/libs/field-guide-site-*.jar 2>/dev/null | head -1)
  if [[ -z "$site_jar" ]]; then
    echo "::error::Site jar not found under build/libs/"
    exit 1
  fi

  rm -rf "$SITE_OUTPUT_DIR"
  local site_args=()
  if [[ -d "${EXPORT_ROOT}/emi" ]]; then
    site_args+=(--emi-dir "${EXPORT_ROOT}/emi")
  fi
  if [[ -n "${RECIPE_BOOK_BASE_URL:-}" ]]; then
    site_args+=(--recipe-book-base-url "${RECIPE_BOOK_BASE_URL}")
  fi
  java -jar "$site_jar" -e "$EXPORT_GUIDE" -o "$SITE_OUTPUT_DIR" "${site_args[@]}"

  if [[ -d "${EXPORT_ROOT}/emi" ]]; then
    rm -rf "${SITE_OUTPUT_DIR}/emi"
    cp -a "${EXPORT_ROOT}/emi" "${SITE_OUTPUT_DIR}/emi"
    echo "Copied EMI bundle to ${SITE_OUTPUT_DIR}/emi"
  else
    echo "::warning::No ${EXPORT_ROOT}/emi — recipe cards will not render"
  fi
}
