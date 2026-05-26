package team.terrafirmgreg.fieldguide.render;

/**
 * 辅助类，用于生成Patchouli列表元素的HTML
 * 根据Patchouli的实现：
 * - $(li) 或 $(li1): 使用 • 符号，基本缩进
 * - $(li2): 使用 ◦ 符号，双倍缩进
 * - $(li3): 使用 • 符号，三倍缩进
 * - 以此类推，奇数用实心圆点，偶数用空心圆点
 */
public class ListHelper {

    /**
     * 生成列表项的开始HTML代码
     * @param level 列表级别（1-9）
     * @return 包含项目符号和缩进的字符串
     */
    public static String generateListItemStart(int level) {
        // 确保级别在1-9之间
        level = Math.max(1, Math.min(level, 9));

        // 计算项目符号：奇数用•，偶数用◦
        char bullet = (level % 2 == 0) ? '\u25E6' : '\u2022';

        // 生成HTML，使用margin-left实现缩进
        if (level > 1) {
            return "<li style=\"margin-left: " + (level - 1) + "em;\">" + bullet + " ";
        } else {
            return "<li>" + bullet + " ";
        }
    }

    /**
     * 检查是否是列表格式化代码
     * @param key 格式化代码键
     * @return 如果是li系列代码返回true
     */
    public static boolean isListFormat(String key) {
        return key.matches("li\\d?");
    }

    /**
     * 从格式化代码中提取列表级别
     * @param key 格式化代码键（如"li", "li2", "li3"）
     * @return 列表级别，如果格式不正确返回1
     */
    public static int extractListLevel(String key) {
        if (!isListFormat(key)) {
            return 1;
        }

        if (key.length() == 2) {
            return 1; // "li" 等同于 "li1"
        }

        char levelChar = key.charAt(2);
        if (Character.isDigit(levelChar)) {
            return Character.digit(levelChar, 10);
        }

        return 1;
    }

    /**
     * 获取列表项的项目符号
     * @param level 列表级别
     * @return 项目符号字符
     */
    public static char getBullet(int level) {
        return (level % 2 == 0) ? '\u25E6' : '\u2022';
    }
}