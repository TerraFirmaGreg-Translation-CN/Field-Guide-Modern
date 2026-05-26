#!/usr/bin/env bash
# 镜像内只跑 MC：xvfb → HeadlessMC → 进世界 → warmup → 导出 → 退出
# 不在此下载 MC/Forge/HeadlessMC，不编译 mod，不 pakku fetch。
set -euo pipefail

WORKDIR="${WORKDIR:-/workspace}"
MODPACK="${MODPACK:-Modpack-Modern}"
FG_EXPORT_DIR="${FG_EXPORT_DIR:-$WORKDIR/export}"
CACHE_DIR="${CACHE_DIR:-$WORKDIR/.cache}"
HMC_JAR="${HMC_JAR:-$CACHE_DIR/headlessmc/headlessmc-launcher-2.9.0.jar}"
WARMUP_TICKS="${FG_EXPORT_WARMUP_TICKS:-2400}"
EXPORT_TIMEOUT="${FG_EXPORT_TIMEOUT_SECONDS:-7200}"
EXPORT_MODE="${FG_EXPORT_MODE:-closure}"
# 与 CI 一致默认不设固定 -Xmx；大整合包 + 容器易 OOM(137)，可用 FG_JVM_HEAP=6G 或调大 Podman 内存
JVM_HEAP="${FG_JVM_HEAP:-}"

cd "$WORKDIR"
mp="$WORKDIR/$MODPACK"

if [ ! -f "$mp/pakku-lock.json" ]; then
  echo "[entrypoint] ERROR: Modpack-Modern 未挂载或子模块未初始化: $mp"
  exit 1
fi

if [ ! -f "$HMC_JAR" ]; then
  echo "[entrypoint] ERROR: HeadlessMC launcher missing: $HMC_JAR"
  echo "  在宿主机运行: ./docker/prepare-cache.sh"
  exit 1
fi
if [ ! -d "$CACHE_DIR/.minecraft/versions" ]; then
  echo "[entrypoint] ERROR: .minecraft not prepared: $CACHE_DIR/.minecraft"
  echo "  在宿主机运行: ./docker/prepare-cache.sh"
  exit 1
fi

# shellcheck source=/workspace/docker/lib/versions.sh
source /workspace/docker/lib/versions.sh
load_mc_forge_versions "$WORKDIR"

mods_count=$(find "$mp/mods" -maxdepth 1 -name '*.jar' 2>/dev/null | wc -l | tr -d ' ')
if [ "${mods_count:-0}" -lt 5 ]; then
  echo "[entrypoint] ERROR: $mp/mods 几乎为空 — 宿主机请先: cd Modpack-Modern && java -jar pakku.jar fetch"
  exit 1
fi

mkdir -p "$mp/mods"
if ! ls "$mp/mods"/field-guide-forge*.jar >/dev/null 2>&1; then
  cp -v /workspace/forge/build/libs/field-guide-forge-*.jar "$mp/mods/"
fi

# HeadlessMC / MC 启动器读取 $HOME/.minecraft
export HOME="/root"
mkdir -p "$HOME"
ln -sfn "$CACHE_DIR/.minecraft" "$HOME/.minecraft"

mkdir -p "$FG_EXPORT_DIR" "$mp/config" "$mp/saves" "$WORKDIR/HeadlessMC"

echo "[entrypoint] modpack=$mp export=$FG_EXPORT_DIR mode=$EXPORT_MODE"
echo "[entrypoint] versions (pakku-lock): mc=$MC_VERSION forge=$FORGE_BUILD"
echo "[entrypoint] .minecraft -> $CACHE_DIR/.minecraft"
echo "[entrypoint] headlessmc=$HMC_JAR"

cp_cfg="$mp/config/craftpresence.json"
if [ -f "$cp_cfg" ]; then
  python3 - "$cp_cfg" <<'PY'
import json, sys
path = sys.argv[1]
with open(path) as f:
    cfg = json.load(f)
cfg.setdefault("displaySettings", {}).setdefault("presenceData", {})["enabled"] = False
cfg.setdefault("advancedSettings", {})["maxConnectionAttempts"] = 1
with open(path, "w") as f:
    json.dump(cfg, f, indent=2)
PY
fi

cp -f "$WORKDIR/forge/config/export-fml.toml" "$mp/config/fml.toml"
cp -f "$WORKDIR/forge/config/export-forge-client.toml" "$mp/config/forge-client.toml"
cat > "$mp/options.txt" <<EOF
onboardAccessibility:false
pauseOnLostFocus:false
EOF

cat > "$WORKDIR/HeadlessMC/config.properties" <<EOF
hmc.java.versions=$JAVA_HOME/bin/java
hmc.gamedir=$mp
hmc.mcdir=$CACHE_DIR/.minecraft
hmc.offline=true
hmc.rethrow.launch.exceptions=true
hmc.exit.on.failed.command=true
EOF

FG_JVM_FLAGS="-XX:+UseContainerSupport"
if [ -n "$JVM_HEAP" ]; then
  FG_JVM_FLAGS+=" -Xmx${JVM_HEAP}"
else
  FG_JVM_FLAGS+=" -XX:MaxRAMPercentage=70"
fi
if [ -n "${HTTP_PROXY:-}" ]; then
  proxy_url="${HTTP_PROXY#http://}"
  proxy_url="${proxy_url#https://}"
  proxy_host="${proxy_url%%:*}"
  proxy_port="${proxy_url##*:}"
  FG_JVM_FLAGS+=" -Dhttp.proxyHost=${proxy_host} -Dhttp.proxyPort=${proxy_port}"
  FG_JVM_FLAGS+=" -Dhttps.proxyHost=${proxy_host} -Dhttps.proxyPort=${proxy_port}"
fi
FG_JVM_FLAGS+=" -Djava.awt.headless=false"
FG_JVM_FLAGS+=" -Dmodernfix.config.mixin.feature.integrated_server_watchdog=false"
FG_JVM_FLAGS+=" -Dfieldguide.runExportAndExit=true"
FG_JVM_FLAGS+=" -Dfieldguide.exportMode=${EXPORT_MODE}"
FG_JVM_FLAGS+=" -Dfieldguide.exportWarmupTicks=${WARMUP_TICKS}"
FG_JVM_FLAGS+=" -Dfieldguide.exportTimeoutSeconds=${EXPORT_TIMEOUT}"
FG_JVM_FLAGS+=" -Dfieldguide.exportFolder=${FG_EXPORT_DIR}"

if [ -n "$JVM_HEAP" ]; then
  echo "[entrypoint] launch (warmup=${WARMUP_TICKS} ticks, -Xmx${JVM_HEAP}) ..."
else
  echo "[entrypoint] launch (warmup=${WARMUP_TICKS} ticks, MaxRAMPercentage=70, 与 CI 相同不设固定 -Xmx) ..."
fi
export ALSOFT_DRIVERS=null
cd "$WORKDIR"
xvfb-run -a java \
  -Dhmc.check.xvfb=true \
  -Dhmc.mcdir="$CACHE_DIR/.minecraft" \
  -Duser.home="$HOME" \
  -jar "$HMC_JAR" \
  --command "launch .*forge.* -regex --jvm \"${FG_JVM_FLAGS}\""

if [ ! -f "$FG_EXPORT_DIR/manifest.json" ]; then
  echo "[entrypoint] ERROR: missing $FG_EXPORT_DIR/manifest.json"
  exit 1
fi

echo "[entrypoint] export OK: $FG_EXPORT_DIR/manifest.json"
python3 - "$FG_EXPORT_DIR" <<'PY'
import json, pathlib, sys
p = pathlib.Path(sys.argv[1])
man = json.loads((p / "manifest.json").read_text())
print("[entrypoint] exportedAt:", man.get("exportedAt"))
print("[entrypoint] exportMode:", man.get("exportMode"))
print("[entrypoint] recipeBundles:", man.get("recipeBundles"))
tfg = p / "data" / "tfg" / "recipes.json"
if tfg.exists():
    r = json.loads(tfg.read_text())
    stubs = sum(1 for v in r.values() if isinstance(v, dict) and v.get("_runtimeStub"))
    kube = sum(1 for v in r.values() if isinstance(v, dict) and v.get("_source") == "kubejs")
    print(f"[entrypoint] tfg recipes: {len(r)} total, kubejs={kube}, stubs={stubs}")
PY
