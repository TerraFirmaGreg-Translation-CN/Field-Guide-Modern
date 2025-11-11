package io.github.tfgcn.fieldguide;

import io.github.tfgcn.fieldguide.exception.InternalException;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
public class ProjectUtil {
    
    private static final Pattern SEARCH_STRIP_PATTERN = Pattern.compile("\\$\\([^)]*\\)");

    /**
     * 从多个键中获取第一个存在的值
     */
    public static Object anyOf(Object data, String... keys) {
        if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            for (String key : keys) {
                if (map.containsKey(key)) {
                    return map.get(key);
                }
            }
        }
        return null;
    }

    /**
     * 路径连接（跨平台）
     */
    public static String pathJoin(String... parts) {
        return Paths.get("", parts).normalize().toString();
    }
    
    /**
     * 清理搜索文本，移除 $(...) 模式
     */
    public static String searchStrip(String input) {
        return SEARCH_STRIP_PATTERN.matcher(input).replaceAll("");
    }
    
    /**
     * 条件检查，不满足时抛出异常
     */
    public static void require(boolean condition, String reason) {
        require(condition, reason, false);
    }
    
    public static void require(boolean condition, String reason, boolean quiet) {
        if (!condition) {
            throw new InternalException(reason, quiet);
        }
    }
}