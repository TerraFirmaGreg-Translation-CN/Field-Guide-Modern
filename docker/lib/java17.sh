# 定位 JDK 17（macOS 用 /usr/libexec/java_home，避免落到过旧的 17.0.x）
locate_java17() {
    if [ "$(uname -s)" = "Darwin" ] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
        local jh
        jh=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
        if [ -n "$jh" ] && [ -x "$jh/bin/java" ]; then
            echo "$jh/bin/java"
            return 0
        fi
    fi
    if [ -n "${JAVA17_HOME:-}" ] && [ -x "$JAVA17_HOME/bin/java" ]; then
        echo "$JAVA17_HOME/bin/java"
        return 0
    fi
    if command -v java >/dev/null 2>&1; then
        echo "$(command -v java)"
        return 0
    fi
    return 1
}
