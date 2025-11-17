package io.github.tfgcn.fieldguide.data.tfc;

import io.github.tfgcn.fieldguide.data.mc.MapColor;
import lombok.Getter;

import java.util.Locale;

@Getter
public enum TFCWood {
    ACACIA(MapColor.TERRACOTTA_ORANGE, MapColor.TERRACOTTA_LIGHT_GRAY),
    ASH(MapColor.TERRACOTTA_PINK, MapColor.TERRACOTTA_ORANGE),
    ASPEN(MapColor.TERRACOTTA_GREEN, MapColor.TERRACOTTA_WHITE),
    BIRCH(MapColor.COLOR_BROWN, MapColor.TERRACOTTA_WHITE),
    BLACKWOOD(MapColor.COLOR_BLACK, MapColor.COLOR_BROWN),
    CHESTNUT(MapColor.TERRACOTTA_RED, MapColor.COLOR_LIGHT_GREEN),
    DOUGLAS_FIR(MapColor.TERRACOTTA_YELLOW, MapColor.TERRACOTTA_BROWN),
    HICKORY(MapColor.TERRACOTTA_BROWN, MapColor.COLOR_GRAY),
    KAPOK(MapColor.COLOR_PURPLE, MapColor.COLOR_BROWN),
    MANGROVE(MapColor.COLOR_RED, MapColor.COLOR_BROWN),
    MAPLE(MapColor.COLOR_ORANGE, MapColor.TERRACOTTA_GRAY),
    OAK(MapColor.WOOD, MapColor.COLOR_BROWN),
    PALM(MapColor.COLOR_ORANGE, MapColor.COLOR_BROWN),
    PINE(MapColor.TERRACOTTA_GRAY, MapColor.COLOR_GRAY),
    ROSEWOOD(MapColor.COLOR_RED, MapColor.TERRACOTTA_LIGHT_GRAY),
    SEQUOIA(MapColor.TERRACOTTA_RED, MapColor.TERRACOTTA_RED),
    SPRUCE(MapColor.TERRACOTTA_PINK, MapColor.TERRACOTTA_BLACK),
    SYCAMORE(MapColor.COLOR_YELLOW, MapColor.TERRACOTTA_LIGHT_GREEN),
    WHITE_CEDAR(MapColor.TERRACOTTA_WHITE, MapColor.TERRACOTTA_LIGHT_GRAY),
    WILLOW(MapColor.COLOR_GREEN, MapColor.TERRACOTTA_BROWN);

    private final String serializedName;
    private final MapColor woodColor;
    private final MapColor barkColor;

    TFCWood(MapColor woodColor, MapColor barkColor) {
        this.serializedName = name().toLowerCase(Locale.ROOT);
        this.woodColor = woodColor;
        this.barkColor = barkColor;
    }
}