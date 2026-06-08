#!/usr/bin/env bash
# Field-Guide-Modern CI — config, release resolution, export, deploy, site build.
# Usage: bash ci/run.sh <command>
set -euo pipefail

CI_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CI_SCRIPTS="${CI_DIR}/scripts"
FGM_ROOT="$(cd "$CI_DIR/.." && pwd)"

ci_node() {
  node "$CI_SCRIPTS/$1" "${@:2}"
}

# GitHub semver release resolution (git ls-remote; empty ci/build.env pin = latest tag).
github_repo_git_url() {
  local spec="${1:?repo required}"
  if [[ "$spec" == https://* ]]; then
    echo "$spec"
    return 0
  fi
  echo "https://github.com/${spec}.git"
}

_semver_strip() {
  echo "${1#v}"
}

_semver_gt() {
  local a b a1 a2 a3 b1 b2 b3
  a="$(_semver_strip "$1")"
  b="$(_semver_strip "$2")"
  IFS=. read -r a1 a2 a3 <<< "$a"
  IFS=. read -r b1 b2 b3 <<< "$b"
  a1=${a1:-0}
  a2=${a2:-0}
  a3=${a3:-0}
  b1=${b1:-0}
  b2=${b2:-0}
  b3=${b3:-0}
  (( a1 > b1 )) && return 0
  (( a1 < b1 )) && return 1
  (( a2 > b2 )) && return 0
  (( a2 < b2 )) && return 1
  (( a3 > b3 )) && return 0
  return 1
}

resolve_latest_semver_release_tag() {
  local repo_spec="${1:?owner/name or git URL required}"
  local git_url best tag

  git_url="$(github_repo_git_url "$repo_spec")"
  best=""
  while IFS= read -r tag; do
    [[ -z "$tag" ]] && continue
    if [[ -z "$best" ]] || _semver_gt "$tag" "$best"; then
      best="$tag"
    fi
  done < <(
    git ls-remote --tags "$git_url" \
      | awk -F/ '{print $NF}' \
      | sed 's/\^{}//' \
      | grep -E '^v?[0-9]+\.[0-9]+\.[0-9]+$'
  )

  if [[ -z "$best" ]]; then
    echo "error: no semver release tags found on ${git_url}" >&2
    return 1
  fi
  echo "$best"
}

resolve_github_release_ref() {
  local repo_spec="${1:?repo required}"
  local pinned="${2:-}"
  if [[ -n "$pinned" ]]; then
    echo "$pinned"
    return 0
  fi
  resolve_latest_semver_release_tag "$repo_spec"
}

resolve_modpack_tag() {
  resolve_github_release_ref \
    "${MODPACK_REPO:-https://github.com/TerraFirmaGreg-Team/Modpack-Modern.git}" \
    "${MODPACK_TAG:-}"
}

resolve_fge_tag() {
  resolve_github_release_ref \
    "${FGE_REPO:-jmecn/field-guide-export}" \
    "${FGE_TAG:-${FGE_VERSION:-}}"
}

resolve_mwe_tag() {
  resolve_github_release_ref \
    "${MWE_REPO:-jmecn/minecraft-web-export}" \
    "${MWE_TAG:-${MWE_VERSION:-}}"
}

load_config() {
  local env_file="${CI_BUILD_ENV:-$CI_DIR/build.env}"
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
      printf 'EXPORT_CACHE_KEY_PREFIX=%s\n' "${EXPORT_CACHE_KEY_PREFIX:-fge-export}"
      printf 'SITE_RELEASE_ASSET_NAME=%s\n' "${SITE_RELEASE_ASSET_NAME:-field-guide-site.tar}"
      printf 'SITE_RELEASE_HASH_LENGTH=%s\n' "${SITE_RELEASE_HASH_LENGTH:-7}"
    } >> "$GITHUB_ENV"
  fi
}

_normalize_version_ref() {
  echo "${1#v}"
}

resolve_hmc_version() {
  load_config
  echo "${HMC_VERSION:?HMC_VERSION required}"
}

resolve_build_version_refs() {
  load_config

  if [[ -z "${MODPACK_TAG:-}" ]]; then
    unset MODPACK_TAG
  fi

  BUILD_REF_MODPACK="$(_normalize_version_ref "$(resolve_modpack_tag)")" || return 1
  BUILD_REF_FGE="$(_normalize_version_ref "$(resolve_fge_tag)")" || return 1
  BUILD_REF_MWE="$(_normalize_version_ref "$(resolve_mwe_tag)")" || return 1
  BUILD_REF_HMC="$(_normalize_version_ref "$(resolve_hmc_version)")" || return 1
}

resolve_build_json_url() {
  if [[ -n "${BUILD_JSON_URL:-}" ]]; then
    echo "$BUILD_JSON_URL"
    return 0
  fi
  if [[ -n "${GITHUB_REPOSITORY:-}" ]]; then
    echo "https://${GITHUB_REPOSITORY%/*}.github.io/${GITHUB_REPOSITORY#*/}/build.json"
    return 0
  fi
  return 1
}

fetch_recorded_build_json() {
  local dest="${1:?dest path required}"
  local url local_site

  if url="$(resolve_build_json_url 2>/dev/null)"; then
    if curl -fsSL --retry 2 --retry-delay 1 "$url" -o "$dest" 2>/dev/null; then
      echo "Loaded published build.json from ${url}" >&2
      return 0
    fi
    echo "No published build.json at ${url} — first deploy or site not ready" >&2
  fi

  local_site="${FGM_ROOT}/${SITE_OUTPUT_DIR:-output}/build.json"
  if [[ -f "$local_site" ]]; then
    cp "$local_site" "$dest"
    echo "Using local ${local_site}" >&2
    return 0
  fi

  echo '{}' > "$dest"
}

_write_build_versions_json() {
  local out="${1:?output path required}"
  local bundle_id="${BUNDLE_ID:?BUNDLE_ID required}"
  local hash_len="${SITE_RELEASE_HASH_LENGTH:-7}"
  resolve_build_version_refs || return 1
  ci_node write-build-versions.mjs \
    "$BUILD_REF_MODPACK" \
    "$BUILD_REF_FGE" \
    "$BUILD_REF_MWE" \
    "$BUILD_REF_HMC" \
    "$bundle_id" \
    "$hash_len" \
    "$out"
}

check_build_changes() {
  local build_json
  build_json="$(mktemp)"
  resolve_build_version_refs || exit 1
  fetch_recorded_build_json "$build_json"

  ci_node check-build-changes.mjs \
    "$build_json" \
    "$BUILD_REF_MODPACK" \
    "$BUILD_REF_FGE" \
    "$BUILD_REF_MWE" \
    "$BUILD_REF_HMC"
  rm -f "$build_json"
}

record_build_versions() {
  local site_dir="${FGM_ROOT}/${SITE_OUTPUT_DIR:-output}"
  local build_json="${BUILD_JSON:-$site_dir/build.json}"
  mkdir -p "$site_dir"
  _write_build_versions_json "$build_json"
  echo "Recorded build versions → ${build_json} (deployed with site)"
  cat "$build_json"
}

publish_site_release() {
  load_config

  local site_dir="${FGM_ROOT}/${SITE_OUTPUT_DIR:-output}"
  local build_json="$site_dir/build.json"
  local asset_name="${SITE_RELEASE_ASSET_NAME:-field-guide-site.tar}"
  local archive="$FGM_ROOT/$asset_name"
  local release_tag notes

  if [[ ! -f "$build_json" ]]; then
    echo "::error::Missing ${build_json} — run record-build-versions first" >&2
    exit 1
  fi

  if [[ ! -f "$site_dir/index.html" ]]; then
    echo "::error::Missing ${site_dir}/index.html — run build-site first" >&2
    exit 1
  fi

  release_tag="$(ci_node read-release-tag.mjs "$build_json")"

  if ! command -v gh >/dev/null 2>&1; then
    echo "::error::gh CLI required to publish site release" >&2
    exit 1
  fi

  if [[ -z "${GH_TOKEN:-}" ]]; then
    echo "::error::GH_TOKEN is required to publish site release" >&2
    exit 1
  fi

  echo "::group::Package site release (${release_tag})"
  rm -f "$archive"
  tar -cf "$archive" -C "$site_dir" .
  echo "Created ${archive} ($(du -h "$archive" | awk '{print $1}'))"
  echo "::endgroup::"

  if gh release view "$release_tag" --repo "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY required}" >/dev/null 2>&1; then
    echo "Release ${release_tag} already exists — skipping upload"
    rm -f "$archive"
    return 0
  fi

  notes="$(mktemp)"
  cp "$build_json" "$notes"

  echo "::group::Create GitHub Release ${release_tag}"
  gh release create "$release_tag" "$archive" \
    --repo "${GITHUB_REPOSITORY}" \
    --title "Field guide site ${release_tag}" \
    --notes-file "$notes"
  rm -f "$notes" "$archive"
  echo "Published ${asset_name} → release ${release_tag}"
  echo "::endgroup::"
}

export_cache_fingerprint() {
  resolve_build_version_refs || return 1
  # Export gate: modpack + field-guide-export + minecraft-web-export
  printf '%s:%s:%s' "$BUILD_REF_MODPACK" "$BUILD_REF_FGE" "$BUILD_REF_MWE" \
    | sha256sum | awk '{print substr($1,1,8)}'
}

export_cache_key() {
  local bundle_id="${1:?bundle_id required}"
  local fingerprint="${2:?fingerprint required}"
  printf '%s-%s-%s' "${EXPORT_CACHE_KEY_PREFIX:-fge-export}" "$bundle_id" "$fingerprint"
}

bundle_id_for_tag() {
  printf 'fg-%s' "${1:?modpack tag required}"
}

_write_bundle_outputs() {
  local tag="${1:?modpack tag required}"
  local label="${2:-bundle}"
  local id cache_key fingerprint

  export MODPACK_TAG="$tag"
  id="$(bundle_id_for_tag "$tag")"
  export BUNDLE_ID="$id"
  fingerprint="$(export_cache_fingerprint)" || exit 1
  cache_key="$(export_cache_key "$id" "$fingerprint")"

  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    {
      echo "bundle_id=${id}"
      echo "modpack_tag=${tag}"
      echo "export_cache_key=${cache_key}"
    } >> "$GITHUB_OUTPUT"
  fi
  if [[ -n "${GITHUB_ENV:-}" ]]; then
    printf 'BUNDLE_ID=%s\n' "$id" >> "$GITHUB_ENV"
  fi
  echo "${label} bundle_id=${id} export_cache_key=${cache_key}"
}

prepare_check_bundle() {
  load_config
  local tag="${MODPACK_TAG:-}"

  if [[ -z "$tag" ]]; then
    tag="$(resolve_modpack_tag)" || exit 1
  fi
  _write_bundle_outputs "$tag" "check"
}

finalize_export_decision() {
  local export_needed=false

  if [[ "${VERSION_EXPORT_NEEDED:-false}" == "true" ]]; then
    export_needed=true
    echo "Export required: version gate" >&2
  elif [[ "${EXPORT_CACHE_HIT:-}" != "true" ]]; then
    export_needed=true
    echo "Export required: cache miss (${EXPORT_CACHE_KEY:-<unset>})" >&2
  else
    echo "Export skipped: versions unchanged and export cache hit" >&2
  fi

  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    echo "export_needed=${export_needed}" >> "$GITHUB_OUTPUT"
  else
    echo "export_needed=${export_needed}"
  fi
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
  _write_bundle_outputs "${MODPACK_TAG:?MODPACK_TAG required}" "export"
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

  if [[ ! -f "$guide/assets/icons/icons.css" ]]; then
    echo "::error::Missing $guide/assets/icons/icons.css"
    exit 1
  fi

  if ! grep -qF -- '--atlas-w:' "$guide/assets/icons/icons.css"; then
    echo "::error::icons.css missing sprite CSS variables (--atlas-w). Re-export with current minecraft-web-export."
    exit 1
  fi

  if ! grep -qF -- '--sprite-x:' "$guide/assets/icons/icons.css"; then
    echo "::error::icons.css missing sprite CSS variables (--sprite-x). Re-export with current minecraft-web-export."
    exit 1
  fi

  if [[ ! -f "$guide/assets/icons/index.json" ]]; then
    echo "::error::Missing $guide/assets/icons/index.json"
    exit 1
  fi

  python3 - <<'PY' "$guide/assets/icons/index.json"
import json, sys
path = sys.argv[1]
with open(path) as f:
    root = json.load(f)
pages = root.get("pages")
if not pages or not isinstance(pages, list):
    raise SystemExit(f"::error::index.json missing pages[] (required for icon atlas scaling)")
for i, page in enumerate(pages):
    if not isinstance(page, dict) or not page.get("width") or not page.get("height"):
        raise SystemExit(f"::error::index.json pages[{i}] missing width/height")
PY

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
  cp -f "$CI_DIR/config/export-fml.toml" "$mp/config/fml.toml"
  cp -f "$CI_DIR/config/export-forge-client.toml" "$mp/config/forge-client.toml"
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
  local id tag

  if [[ -n "${BUNDLE_ID_INPUT:-}" ]]; then
    id="$BUNDLE_ID_INPUT"
  elif [[ -f "$FGM_ROOT/export-meta/bundle-id" ]]; then
    id="$(tr -d '\r\n' < "$FGM_ROOT/export-meta/bundle-id")"
  elif [[ -n "${MODPACK_TAG:-}" ]]; then
    id="$(bundle_id_for_tag "$MODPACK_TAG")"
  else
    load_config
    tag="$(resolve_modpack_tag)"
    if [[ -z "$tag" ]]; then
      echo "::error::Could not resolve modpack tag for bundle id" >&2
      exit 1
    fi
    id="$(bundle_id_for_tag "$tag")"
    export MODPACK_TAG="$tag"
  fi

  export BUNDLE_ID="$id"

  local fingerprint cache_key
  fingerprint="$(export_cache_fingerprint)" || exit 1
  cache_key="$(export_cache_key "$id" "$fingerprint")"

  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    {
      echo "bundle_id=${id}"
      echo "export_cache_key=${cache_key}"
    } >> "$GITHUB_OUTPUT"
  fi
  if [[ -n "${GITHUB_ENV:-}" ]]; then
    printf 'BUNDLE_ID=%s\n' "$id" >> "$GITHUB_ENV"
  fi
  echo "deploy bundle_id=${id} export_cache_key=${cache_key}"
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

usage() {
  cat <<'EOF'
Usage: bash ci/run.sh <command>

Workflow composites:
  prepare-check-bundle, check-build-changes, finalize-export-decision
  prepare-export      env + modpack checkout + bundle id + resolve FGE/MWE tags
  prepare-game        xvfb deps + FGE/MWE jars + HeadlessMC
  finalize-export     export-meta + tar (needs BUNDLE_ID, MODPACK_TAG)
  prepare-deploy      env + resolve bundle id + export cache key
  extract-bundle      restore export cache → EXPORT_ROOT (needs BUNDLE_ID)
  record-build-versions, publish-site-release
  install-bundle      extract or fetch (ACQUIRE=extract|fetch, BUNDLE_ID; local only)

Granular (local debugging):
  env, print-versions, checkout-modpack, prepare-bundle-id, export-languages,
  install-mods, setup-hmc, launch-export, write-export-meta,
  resolve-bundle-id, extract-bundle, fetch-bundle, build-site
EOF
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  cmd="${1:-}"
  if [[ -z "$cmd" ]]; then
    usage >&2
    exit 1
  fi
  shift

  case "$cmd" in
    env) load_config "$@" ;;
    print-versions) print_versions "$@" ;;
    prepare-check-bundle) prepare_check_bundle "$@" ;;
    check-build-changes) check_build_changes "$@" ;;
    finalize-export-decision) finalize_export_decision "$@" ;;
    prepare-export) prepare_export "$@" ;;
    prepare-game) prepare_game "$@" ;;
    finalize-export) finalize_export "$@" ;;
    prepare-deploy) prepare_deploy "$@" ;;
    record-build-versions) record_build_versions "$@" ;;
    publish-site-release) publish_site_release "$@" ;;
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
fi
