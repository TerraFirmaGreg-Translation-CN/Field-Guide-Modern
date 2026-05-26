#!/usr/bin/env bash
# 构建 modpack-export:<6位 git commit>（.cache + forge jar；Modpack-Modern 运行时挂载）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/container.sh
source "$SCRIPT_DIR/lib/container.sh"
container_detect || exit 1

cd "$REPO_ROOT"

GIT_SHA="$(git rev-parse --short=6 HEAD)"
IMAGE="modpack-export:${GIT_SHA}"

fail() { echo "error: $1" >&2; exit 1; }

[ -f Modpack-Modern/pakku-lock.json ] || fail "missing Modpack-Modern"

jar="$(ls forge/build/libs/field-guide-forge-*.jar 2>/dev/null | head -1)"
[ -n "$jar" ] && [ -f "$jar" ] || fail "forge jar not found, run: ./gradlew :forge:reobfJar"

[ -f .cache/headlessmc/headlessmc-launcher-2.9.0.jar ] || fail "missing .cache/headlessmc/headlessmc-launcher-2.9.0.jar，请运行: ./docker/prepare-cache.sh"

# shellcheck source=lib/versions.sh
source "$SCRIPT_DIR/lib/versions.sh"
# shellcheck source=lib/mc-cache.sh
source "$SCRIPT_DIR/lib/mc-cache.sh"
load_mc_forge_versions "$REPO_ROOT"
mc_cache_verify "$REPO_ROOT/.cache/.minecraft" "$MC_VERSION" "$FORGE_BUILD" \
  || fail "Forge/Minecraft not found, run: ./docker/prepare-cache.sh"

# shellcheck source=lib/proxy.sh
source "$SCRIPT_DIR/lib/proxy.sh"
proxy_export_env

BUILD_ARGS=(-f docker/Dockerfile -t "$IMAGE" .)
if [ -n "${HTTP_PROXY:-}" ]; then
    BUILD_ARGS+=(--build-arg "HTTP_PROXY=${HTTP_PROXY}" --build-arg "HTTPS_PROXY=${HTTPS_PROXY}")
fi

container_detect || fail "neither podman nor docker found"
echo "building ${IMAGE} (${DOCKER_CMD})..."
echo "  context excludes Modpack-Modern/mods (mounted at run time)"
"$DOCKER_CMD" build "${BUILD_ARGS[@]}"

echo "completed: $IMAGE"
