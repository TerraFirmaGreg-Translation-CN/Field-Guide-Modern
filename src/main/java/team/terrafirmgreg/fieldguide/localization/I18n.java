package team.terrafirmgreg.fieldguide.localization;

public final class I18n {

    public static final String TITLE = key("title");
    public static final String SHORT_TITLE = key("short_title");
    public static final String INDEX = key("index");
    public static final String CONTENTS = key("contents");
    public static final String GITHUB = key("github");
    public static final String DISCORD = key("discord");
    public static final String CATEGORIES = key("categories");
    public static final String HOME = key("home");
    public static final String MULTIBLOCK = key("multiblock");
    public static final String MULTIBLOCK_ONLY_IN_GAME = key("multiblock_only_in_game");
    public static final String RECIPE = key("recipe");
    public static final String RECIPE_ONLY_IN_GAME = key("recipe_only_in_game");
    public static final String ITEM = key("item");
    public static final String ITEMS = key("items");
    public static final String ITEM_ONLY_IN_GAME = key("item_only_in_game");
    public static final String TICKS = key("ticks");
    public static final String TAG = key("tag");

    public static final String KEY_INVENTORY = key("key.inventory");
    public static final String KEY_ATTACK = key("key.attack");
    public static final String KEY_USE = key("key.use");
    public static final String KEY_DROP = key("key.drop");
    public static final String KEY_SNEAK = key("key.sneak");
    public static final String KEY_SWAP_OFFHAND = key("key.swapOffhand");
    public static final String KEY_CYCLE_CHISEL_MODE = key("tfc.key.cycle_chisel_mode");
    public static final String KEY_PLACE_BLOCK = key("tfc.key.place_block");

    public static final String[] KEYS = {KEY_INVENTORY, KEY_ATTACK, KEY_USE, KEY_DROP, KEY_SNEAK, KEY_SWAP_OFFHAND, KEY_CYCLE_CHISEL_MODE, KEY_PLACE_BLOCK};

    private I18n() {
    }

    public static String key(String text) {
        return "field_guide." + text;
    }
}