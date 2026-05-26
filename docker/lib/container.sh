# 选择 podman 或 docker（优先 podman）
# 用法: source docker/lib/container.sh && container_detect

container_detect() {
    if [ -n "${DOCKER_CMD:-}" ]; then
        return 0
    fi
    if command -v podman >/dev/null 2>&1; then
        DOCKER_CMD=podman
    elif command -v docker >/dev/null 2>&1; then
        DOCKER_CMD=docker
    else
        echo "错误：未找到 podman 或 docker（请安装并确保在 PATH 中）" >&2
        echo "  PATH=$PATH" >&2
        return 1
    fi
    export DOCKER_CMD
    return 0
}
