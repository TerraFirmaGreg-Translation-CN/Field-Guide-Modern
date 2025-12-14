package su.terrafirmgreg.fieldguide.data.agedalcohol;

import lombok.Getter;

import java.util.Locale;

@Getter
public enum AgedAlcohol {
    BEER(0xC4B1B7),
    CIDER(0xAD2A32),
    RUM(0x6D1B23),
    SAKE(0xB6D4BC),
    VODKA(0xDCBEDC),
    WHISKEY(0x572D99),
    CORN_WHISKEY(0xD8CBB7),
    RYE_WHISKEY(0xC69C51);

    private final String id;
    private final int color;

    AgedAlcohol(int color)
    {
        this.id = "aged_" + this.name().toLowerCase(Locale.ROOT);
        this.color = color;
    }

    public String getId()
    {
        return this.id;
    }

    public int getColor()
    {
        return this.color;
    }
}