# 在已有 JAVA 变量前提下附带代理 JVM 参数执行 java
# 需已 source docker/lib/proxy.sh
# 用法: java_with_proxy -jar foo.jar ...

java_with_proxy() {
    local -a jvm_proxy=()
    local line
    while IFS= read -r line; do
        [ -n "$line" ] && jvm_proxy+=("$line")
    done < <(proxy_java_args)
    if [ "${#jvm_proxy[@]}" -gt 0 ]; then
        "$JAVA" "${jvm_proxy[@]}" "$@"
    else
        "$JAVA" "$@"
    fi
}
