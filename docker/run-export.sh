#!/usr/bin/env bash
# 运行 modpack-export 容器（挂载 Modpack-Modern、.cache、export；可选挂载新 forge/cli jar）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/container.sh
source "$SCRIPT_DIR/lib/container.sh"
container_detect || exit 1

cd "$REPO_ROOT"

GIT_SHA="$(git rev-parse --short=6 HEAD)"
IMAGE="${IMAGE:-modpack-export:${GIT_SHA}}"
EXPORT_DIR="$REPO_ROOT/export"
CACHE_DIR="$REPO_ROOT/.cache"
MODPACK_DIR="$REPO_ROOT/Modpack-Modern"

DO_BUILD=1
EXTRA_MOUNTS=()

# shellcheck source=lib/proxy.sh
source "$SCRIPT_DIR/lib/proxy.sh"

usage() {
  sed -n '2,20p' "$0"
  echo ""
  echo "环境变量:"
  echo "  FG_PROXY=http://127.0.0.1:7890  强制代理（默认自动探测 127.0.0.1:7890）"
  echo "  FG_EXPORT_MODE=closure|full   默认 closure"
  echo "  FG_EXPORT_WARMUP_TICKS=2400"
  echo "  FG_EXPORT_TIMEOUT_SECONDS=7200"
  echo "  FG_JVM_HEAP=6G          可选，默认不设 -Xmx（与 CI 一致，MaxRAMPercentage=70）"
  echo "  FG_CONTAINER_MEMORY=10g  容器内存上限（Podman/Docker）"
  echo "  FIELD_GUIDE_JAR=/path/to/field-guide-forge.jar  覆盖 modpack mods 内 jar"
  echo "  CLI_JAR=/path/to/cli.jar                        挂载到容器 /workspace/cli.jar"
  echo "  FG_MC_ASSETS_DIR=...  挂载到 /workspace/.cache/.minecraft/assets（默认 .cache/.minecraft/assets）"
  echo "  IMAGE=modpack-export:<tag>  覆盖镜像名"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --no-build) DO_BUILD=0 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1"; usage; exit 1 ;;
  esac
  shift
done

mkdir -p "$EXPORT_DIR"
[ -d "$MODPACK_DIR" ] || { echo "错误：缺少 Modpack-Modern 子模块"; exit 1; }
[ -f "$MODPACK_DIR/pakku-lock.json" ] || { echo "错误：Modpack-Modern 未初始化"; exit 1; }

mods_count=$(find "$MODPACK_DIR/mods" -maxdepth 1 -name '*.jar' 2>/dev/null | wc -l | tr -d ' ')
if [ "${mods_count:-0}" -lt 5 ]; then
  echo "错误：Modpack-Modern/mods 几乎为空，请先: cd Modpack-Modern && java -jar pakku.jar fetch"
  exit 1
fi

if [ -z "${FIELD_GUIDE_JAR:-}" ]; then
  built_jar="$(ls "$REPO_ROOT"/forge/build/libs/field-guide-forge-*.jar 2>/dev/null | head -1)"
  if [ -n "$built_jar" ] && [ -f "$built_jar" ]; then
    FIELD_GUIDE_JAR="$built_jar"
  fi
fi

if [ "$DO_BUILD" = 1 ]; then
  "$SCRIPT_DIR/build-image.sh"
fi

if ! "$DOCKER_CMD" image exists "$IMAGE" >/dev/null 2>&1; then
  echo "错误：镜像不存在: $IMAGE （当前 commit ${GIT_SHA}，可先 ./docker/build-image.sh）"
  exit 1
fi

MOUNTS=(
  -v "$MODPACK_DIR:/workspace/Modpack-Modern"
  -v "$CACHE_DIR:/workspace/.cache"
  -v "$EXPORT_DIR:/workspace/export"
)

if [ -n "${FIELD_GUIDE_JAR:-}" ]; then
  [ -f "$FIELD_GUIDE_JAR" ] || { echo "错误：FIELD_GUIDE_JAR 不存在"; exit 1; }
  MOUNTS+=(-v "$FIELD_GUIDE_JAR:/workspace/Modpack-Modern/mods/field-guide-forge.jar:ro")
  echo "挂载 field-guide jar: $FIELD_GUIDE_JAR"
fi

if [ -n "${CLI_JAR:-}" ]; then
  [ -f "$CLI_JAR" ] || { echo "错误：CLI_JAR 不存在"; exit 1; }
  MOUNTS+=(-v "$CLI_JAR:/workspace/cli.jar:ro")
  echo "挂载 cli jar: $CLI_JAR → /workspace/cli.jar"
fi

proxy_export_env
RUN_ENV=(
  -e "FG_EXPORT_MODE=${FG_EXPORT_MODE:-closure}"
  -e "FG_EXPORT_WARMUP_TICKS=${FG_EXPORT_WARMUP_TICKS:-2400}"
  -e "FG_EXPORT_TIMEOUT_SECONDS=${FG_EXPORT_TIMEOUT_SECONDS:-7200}"
  -e "FG_JVM_HEAP=${FG_JVM_HEAP:-}"
)
CONTAINER_MEM="${FG_CONTAINER_MEMORY:-10g}"
RUN_OPTS=(--rm -it --init --shm-size=2g --memory="$CONTAINER_MEM")
CONTAINER_PROXY="$(proxy_for_container || true)"
if [ -n "$CONTAINER_PROXY" ]; then
  RUN_ENV+=(
    -e "HTTP_PROXY=$CONTAINER_PROXY"
    -e "HTTPS_PROXY=$CONTAINER_PROXY"
    -e "http_proxy=$CONTAINER_PROXY"
    -e "https_proxy=$CONTAINER_PROXY"
    -e "NO_PROXY=localhost,127.0.0.1,::1"
  )
  echo "  容器代理: $CONTAINER_PROXY"
fi

container_detect || exit 1
echo "运行 ${IMAGE} (${DOCKER_CMD})..."
echo "  导出: $EXPORT_DIR"
echo "  模式: ${FG_EXPORT_MODE:-closure}"
echo "  容器内存: ${CONTAINER_MEM}（退出码 137 多为 OOM，可加大 FG_CONTAINER_MEMORY 或 podman machine set --memory 12288）"

"$DOCKER_CMD" run "${RUN_OPTS[@]}" \
  "${MOUNTS[@]}" \
  "${RUN_ENV[@]}" \
  "$IMAGE"

echo "完成: $EXPORT_DIR/manifest.json"
