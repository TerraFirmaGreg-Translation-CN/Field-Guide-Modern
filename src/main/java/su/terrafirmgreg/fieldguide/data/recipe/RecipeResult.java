package su.terrafirmgreg.fieldguide.data.recipe;

import lombok.Data;

import java.util.List;

@Data
public class RecipeResult {
    private String item;
    private String tag;
    private Integer count;
    private List<Object> modifiers;

    // 有些情况下，result可能是一个嵌套对象，比如stack
    private Stack stack;

    @Data
    public static class Stack {
        private String item;
        private Integer count;
        private List<Object> modifiers;
    }

    public String getItemId() {
        if (item != null) return item;
        if (tag != null) return "#" + tag;
        if (stack != null && stack.getItem() != null) return stack.getItem();
        return null;
    }

    public int getCount() {
        if (count != null) return count;
        if (stack != null && stack.getCount() != null) return stack.getCount();
        return 1;
    }
}