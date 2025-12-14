package su.terrafirmgreg.fieldguide.data.minecraft;

import lombok.Getter;

/**
 * desc:
 *
 * @author yanmaoyuan
 */
@Getter
public enum DyeColor {
    WHITE(0, "white", 0xF9FFFE),
    ORANGE(1, "orange", 0xF9801D),
    MAGENTA(2, "magenta", 0xC74EBD),
    LIGHT_BLUE(3, "light_blue", 0x3AB3DA),
    YELLOW(4, "yellow", 0xFED83D),
    LIME(5, "lime", 0x80C71F),
    PINK(6, "pink", 0xF38BAA),
    GRAY(7, "gray", 0x474F52),
    LIGHT_GRAY(8, "light_gray", 0x9D9D97),
    CYAN(9, "cyan", 0x169C9C),
    PURPLE(10, "purple", 0x8932B8),
    BLUE(11, "blue", 0x3C44AA),
    BROWN(12, "brown", 0x835432),
    GREEN(13, "green", 0x5E7C16),
    RED(14, "red", 0xB02E26),
    BLACK(15, "black", 0x1D1D21)
    ;

    private final int id;
    private final String name;
    private final int color;
    DyeColor(int id, String name, int color) {
        this.id = id;
        this.name = name;
        this.color = color;
    }
}
