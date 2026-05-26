#!/usr/bin/env bash
#
# buildBook.sh — local mirror of .github/workflows/experiment-headless-client.yml
#
# Reproduces the CI auto-export pipeline on a developer machine so you can
# iterate on the forge mod / patchouli loader / world creator without pushing
# and waiting on GitHub Actions.
#
# Pipeline:
#   1. pakku fetch                            (idempotent)
#   2. ./gradlew :forge:reobfJar              (incremental)
#   3. copy field-guide-forge-*.jar into Modpack-Modern/mods
#   4. download + install HMC, MC, Forge      (skipped if present)
#   5. write HeadlessMC/config.properties     (each run; harmless)
#   6. Mod Director audit + strip *.curse.json (backups kept as *.bak)
#   7. CraftPresence patch                    (backups kept as *.bak)
#   8. copy forge/config/export-*.toml into Modpack-Modern/config (backups kept)
#   9. launch HMC and run /fieldguide export
#  10. verify forge/build/guide-export/manifest.json exists
#
# Idempotent — safe to re-run. All modpack-side edits keep .bak files so
# `git status` inside the submodule is restorable via `restoreModpackConfig`.
#
# Env overrides:
#   MC_VERSION       (default 1.20.1)
#   FORGE_BUILD      (default 47.4.13, keep in sync with pakku-lock.json)
#   HMC_VERSION      (default 2.9.0)
#   HMC_MC_DIR       (default ~/.minecraft; HMC stores MC + Forge here)
#   JAVA17_HOME      (default: auto-detected; on macOS prefer /usr/libexec/java_home -v 17)
#   MC_GAME_JVM_EXTRA  optional extra flags for the *Minecraft* child JVM (after buildBook defaults)
#   FG_EXPORT_DIR    (default forge/build/guide-export)
#   SKIP_PAKKU=1     skip step 1 (use whatever's already in Modpack-Modern/mods)
#   SKIP_BUILD=1     skip step 2 (use whatever's already in forge/build/libs)
#   SKIP_LAUNCH=1    skip steps 9-10 (build only)
#   FG_EXPORT_TIMEOUT_SECONDS  (default 7200 — entire auto-export hard limit)

set -euo pipefail

: "${MC_VERSION:=1.20.1}"
: "${FORGE_BUILD:=47.4.13}"
: "${HMC_VERSION:=2.9.0}"
: "${HMC_MC_DIR:=$HOME/.minecraft}"

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$ROOT"

MODPACK="$ROOT/Modpack-Modern"
FG_EXPORT_DIR="${FG_EXPORT_DIR:-$ROOT/forge/build/guide-export}"
HMC_DIR="$ROOT/.local"
HMC_JAR="$HMC_DIR/headlessmc-launcher-$HMC_VERSION.jar"

if [ ! -d "$MODPACK" ]; then
    echo "❌ $MODPACK not found. Run: git submodule update --init --recursive" >&2
    exit 1
fi
mkdir -p "$HMC_DIR" "$FG_EXPORT_DIR"

# ----------------------------------------------------------------------------
# Locate JDK 17 — HMC + MC must run on Java 17.
# ----------------------------------------------------------------------------
locate_java17() {
    # On macOS, prefer Apple's java_home — it picks the *highest* installed Java 17.x.
    # A bare glob like jdk-17*.jdk often lands on an ancient 17.0.2 (SIGSEGV risk with LWJGL).
    if [ "$(uname -s)" = "Darwin" ] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
        local jh
        jh=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
        if [ -n "$jh" ] && [ -x "$jh/bin/java" ]; then
            echo "$jh/bin/java"; return
        fi
    fi
    if [ -n "${JAVA17_HOME-}" ] && [ -x "$JAVA17_HOME/bin/java" ]; then
        echo "$JAVA17_HOME/bin/java"; return
    fi
    local candidate
    for candidate in \
        /Library/Java/JavaVirtualMachines/jdk-17*.jdk/Contents/Home \
        /Library/Java/JavaVirtualMachines/temurin-17*.jdk/Contents/Home \
        /usr/lib/jvm/temurin-17-jdk \
        /usr/lib/jvm/java-17-openjdk*; do
        if [ -x "$candidate/bin/java" ]; then
            echo "$candidate/bin/java"; return
        fi
    done
    return 1
}

JAVA=$(locate_java17 || true)
if [ -z "$JAVA" ]; then
    echo "❌ Could not find a JDK 17 (set JAVA17_HOME to override)." >&2
    exit 1
fi
echo "Using java: $JAVA ($("$JAVA" -version 2>&1 | head -1))"
ver_line=$("$JAVA" -version 2>&1 | head -1 || true)
# Warn on very old 17.0.x (font/LWJGL native crashes are common on 17.0.2).
if echo "$ver_line" | grep -qE 'version "17\.0\.([0-6])"'; then
    echo "⚠️  JDK 17.0.6 or older detected. If Minecraft crashes with SIGSEGV during font/render init," >&2
    echo "   install Temurin 17.0.10+ (https://adoptium.net/) or run: /usr/libexec/java_home -V" >&2
fi

# ----------------------------------------------------------------------------
# Platform: macOS = native window, Linux = xvfb if no DISPLAY.
# Do not use "${empty[@]}" under `set -u` — some Bash builds treat it as unbound.
# ----------------------------------------------------------------------------
OS=$(uname -s)
USE_XVFB=0
if [ "$OS" = "Linux" ] && [ -z "${DISPLAY-}" ]; then
    if ! command -v xvfb-run >/dev/null 2>&1; then
        echo "❌ Headless Linux but xvfb-run not installed. Run: sudo apt install -y xvfb libgl1 libopenal1" >&2
        exit 1
    fi
    USE_XVFB=1
fi

# ----------------------------------------------------------------------------
# Step 1 — pakku fetch
# ----------------------------------------------------------------------------
if [ "${SKIP_PAKKU-0}" = "1" ]; then
    echo "==> [1/10] pakku fetch (SKIPPED via SKIP_PAKKU=1)"
else
    echo "==> [1/10] pakku fetch (idempotent — only downloads missing mods)"
    ( cd "$MODPACK" && "$JAVA" -jar pakku.jar fetch )
fi

# ----------------------------------------------------------------------------
# Step 2 — build forge mod
# ----------------------------------------------------------------------------
if [ "${SKIP_BUILD-0}" = "1" ]; then
    echo "==> [2/10] gradle :forge:reobfJar (SKIPPED via SKIP_BUILD=1)"
else
    echo "==> [2/10] gradle :forge:reobfJar"
    ./gradlew :forge:reobfJar --parallel --build-cache
fi

# ----------------------------------------------------------------------------
# Step 3 — copy mod into modpack/mods
# ----------------------------------------------------------------------------
echo "==> [3/10] copy field-guide-forge jar into modpack"
mkdir -p "$MODPACK/mods"
find "$MODPACK/mods" -maxdepth 1 -name 'field-guide-forge*.jar' -delete
JAR=$(ls forge/build/libs/field-guide-forge-*.jar 2>/dev/null | head -1 || true)
if [ -z "$JAR" ]; then
    echo "❌ no forge/build/libs/field-guide-forge-*.jar (build failed?)" >&2
    exit 1
fi
cp -v "$JAR" "$MODPACK/mods/"

# ----------------------------------------------------------------------------
# Step 4 — download HMC + install MC + Forge
# ----------------------------------------------------------------------------
echo "==> [4/10] HeadlessMC ($HMC_VERSION) launcher"
if [ ! -f "$HMC_JAR" ]; then
    URL="https://github.com/3arthqu4ke/headlessmc/releases/download/$HMC_VERSION/headlessmc-launcher-$HMC_VERSION.jar"
    echo "    downloading $URL"
    curl -fL -o "$HMC_JAR" "$URL"
fi

# ----------------------------------------------------------------------------
# Step 5 — HMC config (mirrors workflow, written every run)
# HMC reads ./HeadlessMC/config.properties relative to CWD, so we cd into ROOT.
# ----------------------------------------------------------------------------
echo "==> [5/10] HMC config (gamedir=$MODPACK, mc=$HMC_MC_DIR)"
mkdir -p "$ROOT/HeadlessMC"
cat > "$ROOT/HeadlessMC/config.properties" <<EOF
hmc.java.versions=$JAVA
hmc.gamedir=$MODPACK
hmc.mcdir=$HMC_MC_DIR
hmc.offline=true
hmc.rethrow.launch.exceptions=true
hmc.exit.on.failed.command=true
EOF

if [ ! -f "$HMC_MC_DIR/versions/$MC_VERSION/$MC_VERSION.json" ]; then
    echo "    installing MC $MC_VERSION via HMC"
    "$JAVA" -jar "$HMC_JAR" --command "download $MC_VERSION"
fi
if ! ls "$HMC_MC_DIR/versions" 2>/dev/null | grep -q "$FORGE_BUILD"; then
    echo "    installing Forge $FORGE_BUILD via HMC"
    "$JAVA" -jar "$HMC_JAR" --command "forge $MC_VERSION --uid $FORGE_BUILD"
fi

# ----------------------------------------------------------------------------
# Step 6 — Mod Director audit + strip (using python; no jq required)
# Idempotent: if *.curse.json.bak already exists we trust the prior run.
# ----------------------------------------------------------------------------
echo "==> [6/10] Mod Director audit + strip"
MD_DIR="$MODPACK/config/mod-director"
LOCK="$MODPACK/pakku-lock.json"
if [ -d "$MD_DIR" ] && ls "$MD_DIR"/*.curse.json >/dev/null 2>&1; then
    python3 - "$MD_DIR" "$LOCK" "$MODPACK/mods" <<'PY'
import json, os, shutil, sys
md_dir, lock_path, mods_dir = sys.argv[1], sys.argv[2], sys.argv[3]
with open(lock_path) as f:
    lock = json.load(f)
addon_to_filename = {}
for proj in lock.get("projects", []):
    cf_id = (proj.get("id") or {}).get("curseforge", "")
    if not cf_id:
        continue
    for file in proj.get("files", []):
        if file.get("type") == "curseforge":
            addon_to_filename[str(cf_id)] = file.get("file_name")
            break
missing = []
for name in sorted(os.listdir(md_dir)):
    if not name.endswith(".curse.json"):
        continue
    path = os.path.join(md_dir, name)
    with open(path) as f:
        cj = json.load(f)
    addon = str(cj.get("addonId"))
    file_id = str(cj.get("fileId"))
    fname = addon_to_filename.get(addon)
    if not fname:
        print(f"  MISSING: {name} (addonId={addon} fileId={file_id}) not declared in pakku-lock.json")
        missing.append(name)
    elif not os.path.isfile(os.path.join(mods_dir, fname)):
        print(f"  MISSING: {fname} (addonId={addon}) in lock but not in mods/")
        missing.append(name)
    else:
        bak = path + ".bak"
        if not os.path.exists(bak):
            shutil.move(path, bak)
            print(f"  STRIPPED: {name} (addonId={addon}, file {fname} OK in mods/) -> {os.path.basename(bak)}")
        else:
            if os.path.exists(path):
                os.remove(path)
            print(f"  already-stripped: {name}")
sys.exit(1 if missing else 0)
PY
else
    echo "    no *.curse.json found, nothing to do"
fi

# ----------------------------------------------------------------------------
# Step 7 — CraftPresence: disable Discord rich presence (10× ~1min retries on miss)
# ----------------------------------------------------------------------------
echo "==> [7/10] CraftPresence patch"
CP="$MODPACK/config/craftpresence.json"
if [ -f "$CP" ]; then
    [ -f "$CP.bak" ] || cp -v "$CP" "$CP.bak"
    python3 - "$CP" <<'PY'
import json, sys
path = sys.argv[1]
with open(path) as f:
    cfg = json.load(f)
cfg.setdefault("displaySettings", {}).setdefault("presenceData", {})["enabled"] = False
cfg.setdefault("advancedSettings", {})["maxConnectionAttempts"] = 1
with open(path, "w") as f:
    json.dump(cfg, f, indent=2)
print(f"  patched {path}: presence disabled, maxConnectionAttempts=1")
PY
else
    echo "    no $CP (skip)"
fi

# ----------------------------------------------------------------------------
# Step 8 — export configs (fml.toml / forge-client.toml / options.txt stub)
# ----------------------------------------------------------------------------
echo "==> [8/10] export configs (fml.toml / forge-client.toml / options.txt)"
mkdir -p "$MODPACK/config" "$MODPACK/saves"
backup_then_copy() {
    local src=$1 dst=$2
    if [ -f "$dst" ] && [ ! -f "$dst.bak" ]; then
        cp -v "$dst" "$dst.bak"
    fi
    cp -fv "$src" "$dst"
}
backup_then_copy forge/config/export-fml.toml "$MODPACK/config/fml.toml"
backup_then_copy forge/config/export-forge-client.toml "$MODPACK/config/forge-client.toml"
if [ ! -f "$MODPACK/options.txt" ]; then
    cat > "$MODPACK/options.txt" <<'EOF'
onboardAccessibility:false
pauseOnLostFocus:false
EOF
    echo "    wrote stub $MODPACK/options.txt"
fi

# ----------------------------------------------------------------------------
# Step 9 — launch
# ----------------------------------------------------------------------------
if [ "${SKIP_LAUNCH-0}" = "1" ]; then
    echo "==> [9/10] launch (SKIPPED via SKIP_LAUNCH=1)"
    echo "✅ Build phase done."
    exit 0
fi

echo "==> [9/10] launch HMC and run /fieldguide export"
echo "    export dir: $FG_EXPORT_DIR"
echo "    (mod auto-creates saves/guide-export, runs export, exits 0)"

# Child JVM flags passed through HMC --jvm (the *game* process, not the HMC launcher).
# macOS: -XstartOnFirstThread is required for LWJGL/GLFW + AWT; without it, SIGSEGV during
# font/unihex load is common. Linux CI uses xvfb and does not need this flag.
# ModernFix integrated_server_watchdog: export blocks server ticks for minutes → spurious thread dumps.
: "${FG_EXPORT_TIMEOUT_SECONDS:=7200}"
CHILD_JVM="-Djava.awt.headless=false -Dmodernfix.config.mixin.feature.integrated_server_watchdog=false -Dfieldguide.runExportAndExit=true -Dfieldguide.exportMode=closure -Dfieldguide.exportFolder=$FG_EXPORT_DIR -Dfieldguide.exportWarmupTicks=2400 -Dfieldguide.exportTimeoutSeconds=$FG_EXPORT_TIMEOUT_SECONDS"
if [ "$OS" = "Darwin" ]; then
    CHILD_JVM="-XstartOnFirstThread ${CHILD_JVM}"
fi
if [ -n "${MC_GAME_JVM_EXTRA-}" ]; then
    CHILD_JVM="${MC_GAME_JVM_EXTRA} ${CHILD_JVM}"
fi
# HMC --command string must match experiment-headless-client.yml (escaped inner quotes).
# macOS adds -XstartOnFirstThread above; Linux CI workflow has no equivalent (xvfb only).
HMC_LAUNCH_CMD="launch .*forge.* -regex --jvm \"${CHILD_JVM}\""

export ALSOFT_DRIVERS=null
if [ "$USE_XVFB" = "1" ]; then
    xvfb-run -a "$JAVA" -Dhmc.check.xvfb=true -jar "$HMC_JAR" --command "$HMC_LAUNCH_CMD"
else
    "$JAVA" -jar "$HMC_JAR" --command "$HMC_LAUNCH_CMD"
fi

# ----------------------------------------------------------------------------
# Step 10 — verify
# ----------------------------------------------------------------------------
echo "==> [10/10] verify"
MANIFEST="$FG_EXPORT_DIR/manifest.json"
if [ -f "$MANIFEST" ]; then
    echo "✅ manifest.json written: $MANIFEST"
    if command -v python3 >/dev/null 2>&1; then
        python3 -c "import json,sys; d=json.load(open('$MANIFEST')); b=d.get('book') or {}; print(f\"   book: {b.get('namespace')}:{b.get('bookId')} ({b.get('language')}) — {b.get('categoryCount',0)} categories, {b.get('entryCount',0)} entries\")"
    fi
else
    echo "❌ $MANIFEST not produced" >&2
    echo "   see Modpack-Modern/logs/latest.log and HeadlessMC/headlessmc.log" >&2
    exit 1
fi
