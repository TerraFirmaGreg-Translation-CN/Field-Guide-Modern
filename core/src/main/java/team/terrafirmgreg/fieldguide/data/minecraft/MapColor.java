package team.terrafirmgreg.fieldguide.data.minecraft;

import com.google.common.base.Preconditions;
import lombok.Getter;

@Getter
public enum MapColor {
    NONE(0),
    GRASS(0x7FB238),
    SAND(0xF7E9A3),
    WOOL(0xC7C7C7),
    FIRE(0xFF0000),
    ICE(0xA0A0FF),
    METAL(0xA7A7A7),
    PLANT(0x007C00),
    SNOW(0xFFFFFF),
    CLAY(0xA4A8B8),
    DIRT(0x976D4D),
    STONE(0x707070),
    WATER(0x4040FF),
    WOOD(0x8F7748),
    QUARTZ(0xFFFCF5),
    COLOR_ORANGE(0xD87F33),
    COLOR_MAGENTA(0xB24CD8),
    COLOR_LIGHT_BLUE(0x6699D8),
    COLOR_YELLOW(0xE5E533),
    COLOR_LIGHT_GREEN(0x7FCC19),
    COLOR_PINK(0xF27FA5),
    COLOR_GRAY(0x4C4C4C),
    COLOR_LIGHT_GRAY(0x999999),
    COLOR_CYAN(0x4C7F99),
    COLOR_PURPLE(0x7F3FB2),
    COLOR_BLUE(0x334CB2),
    COLOR_BROWN(0x664C33),
    COLOR_GREEN(0x667F33),
    COLOR_RED(0x993333),
    COLOR_BLACK(0x191919),
    GOLD(0xFAEE4D),
    DIAMOND(0x5CDBD5),
    LAPIS(0x4A80FF),
    EMERALD(0xD93A),
    PODZOL(0x815631),
    NETHER(0x700200),
    TERRACOTTA_WHITE(0xD1B1A1),
    TERRACOTTA_ORANGE(0x9F5224),
    TERRACOTTA_MAGENTA(0x95576C),
    TERRACOTTA_LIGHT_BLUE(0x706C8A),
    TERRACOTTA_YELLOW(0xBA8524),
    TERRACOTTA_LIGHT_GREEN(0x677535),
    TERRACOTTA_PINK(0xA04D4E),
    TERRACOTTA_GRAY(0x392923),
    TERRACOTTA_LIGHT_GRAY(0x876B62),
    TERRACOTTA_CYAN(0x575C5C),
    TERRACOTTA_PURPLE(0x7A4958),
    TERRACOTTA_BLUE(0x4C3E5C),
    TERRACOTTA_BROWN(0x4C3223),
    TERRACOTTA_GREEN(0x4C522A),
    TERRACOTTA_RED(0x8E3C2E),
    TERRACOTTA_BLACK(0x251610),
    CRIMSON_NYLIUM(0xBD3031),
    CRIMSON_STEM(0x943F61),
    CRIMSON_HYPHAE(0x5C191D),
    WARPED_NYLIUM(0x167E86),
    WARPED_STEM(0x3A8E8C),
    WARPED_HYPHAE(0x562C3E),
    WARPED_WART_BLOCK(0x14B485),
    DEEPSLATE(0x646464),
    RAW_IRON(0xD8AF93),
    GLOW_LICHEN(0x7FA796);
    public final int col;

    MapColor(int col) {
        this.col = col;
    }

    public int calculateRGBColor(Brightness brightness) {
        if (this == NONE) {
            return 0;
        } else {
            int i = brightness.modifier;
            int r = (this.col >> 16 & 255) * i / 255; r = Math.min(255, Math.max(0, r));
            int g = (this.col >> 8 & 255) * i / 255; g = Math.min(255, Math.max(0, g));
            int b = (this.col & 255) * i / 255; b = Math.min(255, Math.max(0, b));
            return r << 16 | g << 8 | b;
        }
    }

    public enum Brightness {
        LOW(0, 180),
        NORMAL(1, 220),
        HIGH(2, 255),
        LOWEST(3, 135),
        HIGHER(4, 311);

        private static final Brightness[] VALUES = new Brightness[]{LOW, NORMAL, HIGH, LOWEST};
        public final int id;
        public final int modifier;

        private Brightness(int id, int modifier) {
            this.id = id;
            this.modifier = modifier;
        }

        public static Brightness byId(int id) {
            Preconditions.checkPositionIndex(id, VALUES.length, "brightness id");
            return byIdUnsafe(id);
        }

        static Brightness byIdUnsafe(int id) {
            return VALUES[id];
        }
    }
}