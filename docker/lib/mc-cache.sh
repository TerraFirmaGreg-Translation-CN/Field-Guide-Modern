# 校验 / 安装 .cache/.minecraft（不依赖 HeadlessMC 的 Prism 元数据，避免 forge 404）

mc_cache_has_vanilla() {
    local mc_home="$1" mc_version="$2"
    [ -f "$mc_home/versions/$mc_version/$mc_version.json" ]
}

mc_cache_has_forge() {
    local mc_home="$1" forge_build="$2"
    local v
    for v in "$mc_home"/versions/*; do
        [ -e "$v" ] || continue
        case "$(basename "$v")" in
            *forge*"${forge_build}"*) return 0 ;;
        esac
    done
    return 1
}

# 音效/字体等均在 assets/objects；仅 indexes 无 objects 时启动仍会联网拉取
mc_cache_has_assets() {
    local mc_home="$1"
    local assets="$mc_home/assets"
    [ -d "$assets/indexes" ] || return 1
    [ -n "$(find "$assets/indexes" -maxdepth 1 -name '*.json' -print -quit 2>/dev/null)" ] || return 1
    [ -n "$(find "$assets/objects" -type f -print -quit 2>/dev/null)" ] || return 1
    return 0
}

mc_cache_verify() {
    local mc_home="$1" mc_version="$2" forge_build="$3"
    if ! mc_cache_has_vanilla "$mc_home" "$mc_version"; then
        echo "错误：缺少原版 $mc_version（$mc_home/versions/$mc_version/）" >&2
        return 1
    fi
    if ! mc_cache_has_forge "$mc_home" "$forge_build"; then
        echo "错误：缺少 Forge $forge_build（$mc_home/versions/ 下无 *forge*${forge_build}*）" >&2
        return 1
    fi
    return 0
}

# 官方 Maven installer（路径 1.20.1-47.4.13，不是 HMC 拼错的 1.20.1-47.4.13-1.20.1）
# 第 5 个参数可选：安装器 java 启动方式，默认直接调用 $java；可传函数名 java_with_proxy
mc_cache_install_forge() {
    local java="$1" mc_home="$2" mc_version="$3" forge_build="$4"
    local java_runner="${5:-}"
    local forge_full="${mc_version}-${forge_build}"
    local url="https://maven.minecraftforge.net/net/minecraftforge/forge/${forge_full}/forge-${forge_full}-installer.jar"
    local tmp
    tmp="$(mktemp -t forge-installer.XXXXXX.jar)"
    echo "[prepare-cache] 下载 Forge installer: $url"
    curl -fsSL -o "$tmp" "$url"
    echo "[prepare-cache] 运行 Forge installer -> $mc_home"
    if [ -n "$java_runner" ] && type "$java_runner" >/dev/null 2>&1; then
        "$java_runner" -jar "$tmp" --installClient --minecraftDir "$mc_home"
    else
        "$java" -jar "$tmp" --installClient --minecraftDir "$mc_home"
    fi
    rm -f "$tmp"
}
