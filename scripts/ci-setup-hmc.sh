#!/usr/bin/env bash
# Download HeadlessMC launcher, write config, fetch MC + Forge if missing.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

HMC_VER="${HMC_VERSION:?HMC_VERSION required}"
MC_VER="${MC_VERSION:?MC_VERSION required}"
FORGE="${FORGE_BUILD:?FORGE_BUILD required}"
MP="${MODPACK_DIR:-Modpack-Modern}"
MP_ABS="$(cd "$ROOT/$MP" && pwd)"
launcher="headlessmc-launcher-${HMC_VER}.jar"

if [[ ! -f "$launcher" ]]; then
  gh release download "$HMC_VER" \
    --repo 3arthqu4ke/headlessmc \
    --pattern "$launcher" \
    --clobber
fi

mkdir -p HeadlessMC
cat > HeadlessMC/config.properties <<EOF
hmc.java.versions=$JAVA_HOME/bin/java
hmc.gamedir=$MP_ABS
hmc.offline=true
hmc.rethrow.launch.exceptions=true
hmc.exit.on.failed.command=true
EOF

if [[ ! -f "$HOME/.minecraft/versions/$MC_VER/$MC_VER.json" ]]; then
  java -jar "$launcher" --command "download $MC_VER"
fi
if ! ls "$HOME/.minecraft/versions" 2>/dev/null | grep -q "$FORGE"; then
  java -jar "$launcher" --command "forge $MC_VER --uid $FORGE"
fi
