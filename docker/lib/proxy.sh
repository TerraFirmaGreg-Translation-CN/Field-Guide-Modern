# 代理：优先已有 HTTP(S)_PROXY；否则探测 127.0.0.1:7890（可用 FG_PROXY_HOST/PORT 覆盖）
# 用法: source docker/lib/proxy.sh && proxy_detect && proxy_export_env

FG_PROXY_HOST="${FG_PROXY_HOST:-127.0.0.1}"
FG_PROXY_PORT="${FG_PROXY_PORT:-7890}"
FG_NO_PROXY="${FG_NO_PROXY:-localhost,127.0.0.1,::1}"

_proxy_url_from_parts() {
    echo "http://${FG_PROXY_HOST}:${FG_PROXY_PORT}"
}

_proxy_already_set() {
    [ -n "${HTTPS_PROXY:-}${https_proxy:-}${HTTP_PROXY:-}${http_proxy:-}${ALL_PROXY:-}${all_proxy:-}" ]
}

_proxy_port_open() {
    local host="$1" port="$2"
    if command -v nc >/dev/null 2>&1; then
        nc -z -w 2 "$host" "$port" >/dev/null 2>&1 && return 0
    fi
    # bash 内置（macOS / Linux 常见）
    (echo >/dev/tcp/"$host"/"$port") >/dev/null 2>&1 && return 0
    return 1
}

_proxy_curl_can_use() {
    local url="$1"
    command -v curl >/dev/null 2>&1 || return 1
    curl -fsS --connect-timeout 3 -x "$url" -o /dev/null \
        "https://www.google.com/generate_204" 2>/dev/null \
        || curl -fsS --connect-timeout 3 -x "$url" -o /dev/null \
        "http://www.gstatic.com/generate_204" 2>/dev/null
}

# 检测并设置 FG_EFFECTIVE_PROXY（不 export，由 proxy_export_env 统一导出）
proxy_detect() {
    if [ -n "${FG_PROXY:-}" ]; then
        FG_EFFECTIVE_PROXY="$FG_PROXY"
        echo "[proxy] 使用 FG_PROXY=$FG_EFFECTIVE_PROXY"
        return 0
    fi
    if _proxy_already_set; then
        FG_EFFECTIVE_PROXY="${HTTPS_PROXY:-${https_proxy:-${HTTP_PROXY:-${http_proxy:-}}}}"
        echo "[proxy] 使用环境变量: $FG_EFFECTIVE_PROXY"
        return 0
    fi
    local candidate
    candidate="$(_proxy_url_from_parts)"
    if _proxy_port_open "$FG_PROXY_HOST" "$FG_PROXY_PORT"; then
        if _proxy_curl_can_use "$candidate"; then
            FG_EFFECTIVE_PROXY="$candidate"
            echo "[proxy] 检测到本地代理 ${FG_PROXY_HOST}:${FG_PROXY_PORT}（curl 探测成功）"
            return 0
        fi
        FG_EFFECTIVE_PROXY="$candidate"
        echo "[proxy] 检测到本地端口 ${FG_PROXY_HOST}:${FG_PROXY_PORT}（未验证出口，仍将使用）"
        return 0
    fi
    FG_EFFECTIVE_PROXY=""
    echo "[proxy] 未配置代理（直连）"
    return 0
}

proxy_export_env() {
    proxy_detect
    if [ -z "${FG_EFFECTIVE_PROXY:-}" ]; then
        return 0
    fi
    export HTTP_PROXY="$FG_EFFECTIVE_PROXY"
    export HTTPS_PROXY="$FG_EFFECTIVE_PROXY"
    export http_proxy="$FG_EFFECTIVE_PROXY"
    export https_proxy="$FG_EFFECTIVE_PROXY"
    export ALL_PROXY="$FG_EFFECTIVE_PROXY"
    export all_proxy="$FG_EFFECTIVE_PROXY"
    export NO_PROXY="$FG_NO_PROXY"
    export no_proxy="$FG_NO_PROXY"
}

# 解析 http://host:port → host port
_proxy_parse_url() {
    local url="$1"
    url="${url#http://}"
    url="${url#https://}"
    _PROXY_HOST="${url%%:*}"
    _PROXY_PORT="${url##*:}"
}

# Java（HeadlessMC / Forge installer）
proxy_java_args() {
    if [ -z "${FG_EFFECTIVE_PROXY:-}" ]; then
        proxy_detect
    fi
    if [ -z "${FG_EFFECTIVE_PROXY:-}" ]; then
        return 0
    fi
    _proxy_parse_url "$FG_EFFECTIVE_PROXY"
    # 每行一个 -D，避免 bash 数组把整行当成单个参数传给 java
    echo "-Dhttp.proxyHost=${_PROXY_HOST}"
    echo "-Dhttp.proxyPort=${_PROXY_PORT}"
    echo "-Dhttps.proxyHost=${_PROXY_HOST}"
    echo "-Dhttps.proxyPort=${_PROXY_PORT}"
}

# curl 使用环境变量即可；显式 -x 备用
proxy_curl_args() {
    if [ -z "${FG_EFFECTIVE_PROXY:-}" ]; then
        proxy_detect
    fi
    if [ -n "${FG_EFFECTIVE_PROXY:-}" ]; then
        echo "-x" "$FG_EFFECTIVE_PROXY"
    fi
}

# 容器内访问宿主机代理（Podman/Docker Desktop on Mac）
proxy_for_container() {
    if [ -n "${FG_EFFECTIVE_PROXY:-}" ]; then
        echo "$FG_EFFECTIVE_PROXY"
        return 0
    fi
    proxy_detect
    if [ -n "${FG_EFFECTIVE_PROXY:-}" ]; then
        local host port
        _proxy_parse_url "$FG_EFFECTIVE_PROXY"
        host="$_PROXY_HOST"
        port="$_PROXY_PORT"
        if [ "$host" = "127.0.0.1" ] || [ "$host" = "localhost" ]; then
            if [ "$(uname -s)" = "Darwin" ]; then
                echo "http://host.docker.internal:${port}"
                return 0
            fi
            if command -v podman >/dev/null 2>&1; then
                echo "http://host.containers.internal:${port}"
                return 0
            fi
        fi
        echo "$FG_EFFECTIVE_PROXY"
    fi
}
