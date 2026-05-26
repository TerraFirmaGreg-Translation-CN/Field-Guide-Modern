# 从 Modpack-Modern/pakku-lock.json 读取 MC/Forge 版本（兼容 macOS 默认 bash 3.2，不用 mapfile）
# 用法: source docker/lib/versions.sh && load_mc_forge_versions "$REPO_ROOT"

load_mc_forge_versions() {
    local root="$1"
    local lock="${root}/Modpack-Modern/pakku-lock.json"
    if [ ! -f "$lock" ]; then
        echo "错误：缺少 $lock" >&2
        return 1
    fi
    local lines
    lines="$(python3 - "$root" <<'PY'
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
lock = json.loads((root / "Modpack-Modern" / "pakku-lock.json").read_text())
print(lock["mc_versions"][0])
print(lock["loaders"]["forge"])
PY
)"
    MC_VERSION="$(printf '%s\n' "$lines" | sed -n '1p')"
    FORGE_BUILD="$(printf '%s\n' "$lines" | sed -n '2p')"
    if [ -z "$MC_VERSION" ] || [ -z "$FORGE_BUILD" ]; then
        echo "错误：无法解析 pakku-lock.json 版本" >&2
        return 1
    fi
}
