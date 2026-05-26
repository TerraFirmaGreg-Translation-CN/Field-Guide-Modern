#!/usr/bin/env bash
# 在 Field-Guide-Modern 宿主机填充 .cache（构建镜像前执行）
# MC：HeadlessMC download；Forge：官方 Maven installer（HMC forge 依赖 Prism 元数据，易 404/超时）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/versions.sh
source "$SCRIPT_DIR/lib/versions.sh"
# shellcheck source=lib/java17.sh
source "$SCRIPT_DIR/lib/java17.sh"
# shellcheck source=lib/mc-cache.sh
source "$SCRIPT_DIR/lib/mc-cache.sh"
# shellcheck source=lib/proxy.sh
source "$SCRIPT_DIR/lib/proxy.sh"
# shellcheck source=lib/run-java.sh
source "$SCRIPT_DIR/lib/run-java.sh"

proxy_export_env

CACHE="$REPO_ROOT/.cache"
HMC_VERSION="${HMC_VERSION:-2.9.0}"
HMC_DIR="$CACHE/headlessmc"
HMC_JAR="$HMC_DIR/headlessmc-launcher-${HMC_VERSION}.jar"
MC_HOME="$CACHE/.minecraft"

load_mc_forge_versions "$REPO_ROOT"

JAVA="$(locate_java17)" || { echo "错误：需要 JDK 17（可设置 JAVA17_HOME）"; exit 1; }
echo "[prepare-cache] java=$JAVA"

mkdir -p "$HMC_DIR" "$MC_HOME"

if [ ! -f "$HMC_JAR" ]; then
  echo "[prepare-cache] 下载 HeadlessMC ${HMC_VERSION} ..."
  curl -fsSL -o "$HMC_JAR" \
    "https://github.com/3arthqu4ke/headlessmc/releases/download/${HMC_VERSION}/headlessmc-launcher-${HMC_VERSION}.jar"
fi

# 可选：从已有 Prism/官方客户端复制（跳过下载）
if [ -n "${COPY_MINECRAFT_FROM:-}" ] && [ -d "${COPY_MINECRAFT_FROM}/versions" ]; then
  echo "[prepare-cache] 从 $COPY_MINECRAFT_FROM 同步 versions / libraries / assets …"
  mkdir -p "$MC_HOME"
  for sub in versions libraries assets; do
    if [ -d "${COPY_MINECRAFT_FROM}/${sub}" ]; then
      rsync -a "${COPY_MINECRAFT_FROM}/${sub}/" "$MC_HOME/${sub}/"
    fi
  done
fi

export HOME="$CACHE/mc-home"
mkdir -p "$HOME"
ln -sfn "$MC_HOME" "$HOME/.minecraft"

mkdir -p "$REPO_ROOT/HeadlessMC"
cat > "$REPO_ROOT/HeadlessMC/config.properties" <<EOF
hmc.java.versions=$JAVA
hmc.gamedir=$REPO_ROOT/Modpack-Modern
hmc.mcdir=$MC_HOME
hmc.offline=true
hmc.rethrow.launch.exceptions=true
hmc.exit.on.failed.command=true
EOF

# HMC 从 CWD 下的 ./HeadlessMC/config.properties 读配置（与 buildBook / CI 一致）
cd "$REPO_ROOT"

_hmc_java() {
  java_with_proxy \
    -Dhmc.mcdir="$MC_HOME" \
    -Duser.home="$HOME" \
    -jar "$HMC_JAR" "$@"
}

if ! mc_cache_has_vanilla "$MC_HOME" "$MC_VERSION"; then
  echo "[prepare-cache] HeadlessMC: download ${MC_VERSION} -> $MC_HOME"
  printf 'y\n' | _hmc_java --command "download ${MC_VERSION}" || true
fi
if ! mc_cache_has_vanilla "$MC_HOME" "$MC_VERSION"; then
  echo "[prepare-cache] ERROR: 原版 ${MC_VERSION} 未安装成功（检查代理或重试 prepare-cache）" >&2
  echo "[prepare-cache] 诊断: MC_HOME=$MC_HOME" >&2
  ls -la "$MC_HOME" 2>/dev/null >&2 || true
  ls -la "$MC_HOME/versions" 2>/dev/null >&2 || true
  echo "[prepare-cache] hint: COPY_MINECRAFT_FROM=\"\$HOME/.minecraft\" ./docker/prepare-cache.sh" >&2
  exit 1
fi

if ! mc_cache_has_forge "$MC_HOME" "$FORGE_BUILD"; then
  _forge_via_maven() {
    mc_cache_install_forge "$JAVA" "$MC_HOME" "$MC_VERSION" "$FORGE_BUILD" java_with_proxy
  }
  if [ "${USE_HMC_FORGE:-1}" = "1" ] && [ -n "${FG_EFFECTIVE_PROXY:-}" ]; then
    echo "[prepare-cache] HeadlessMC: forge ${MC_VERSION} --uid ${FORGE_BUILD} (proxy)"
    if ! _hmc_java --command "forge ${MC_VERSION} --uid ${FORGE_BUILD}"; then
      echo "[prepare-cache] HMC forge failed, using Maven installer ..."
      _forge_via_maven
    fi
  elif [ "${USE_HMC_FORGE:-0}" = "1" ]; then
    echo "[prepare-cache] HeadlessMC: forge ${MC_VERSION} --uid ${FORGE_BUILD}"
    if ! _hmc_java --command "forge ${MC_VERSION} --uid ${FORGE_BUILD}"; then
      _forge_via_maven
    fi
  else
    _forge_via_maven
  fi
fi

if ! mc_cache_verify "$MC_HOME" "$MC_VERSION" "$FORGE_BUILD"; then
  echo "[prepare-cache] hint: COPY_MINECRAFT_FROM=\"\$HOME/.minecraft\" ./docker/prepare-cache.sh" >&2
  exit 1
fi

mkdir -p "$MC_HOME/assets"
if ! mc_cache_has_assets "$MC_HOME"; then
  echo "[prepare-cache] 提示: 尚无 assets（音效/字体等）" >&2
  echo "[prepare-cache]   可从本机客户端复制: COPY_MINECRAFT_FROM=\"\$HOME/.minecraft\" ./docker/prepare-cache.sh" >&2
  echo "[prepare-cache]   或首次容器运行后写入 .cache/.minecraft/assets，之后 run-export 会挂载复用" >&2
else
  echo "[prepare-cache] assets OK ($(du -sh "$MC_HOME/assets" 2>/dev/null | awk '{print $1}'))"
fi

echo "[prepare-cache] OK"
echo "  .minecraft: $MC_HOME"
echo "  versions: $(ls "$MC_HOME/versions" | tr '\n' ' ')"
echo "  assets: $MC_HOME/assets"
echo "  headlessmc: $HMC_JAR"
