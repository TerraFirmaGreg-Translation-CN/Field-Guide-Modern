package su.terrafirmgreg.fieldguide.data.gtceu;

import su.terrafirmgreg.fieldguide.data.minecraft.ResourceLocation;
import su.terrafirmgreg.fieldguide.data.gtceu.utils.FormattingUtil;

public class GTCEu {

    public static final String MOD_ID = "gtceu";
    private static final ResourceLocation TEMPLATE_LOCATION = new ResourceLocation(MOD_ID, "");
    public static final String NAME = "GregTechCEu";


    public static ResourceLocation id(String path) {
        if (path.isBlank()) {
            return TEMPLATE_LOCATION;
        }

        int i = path.indexOf(':');
        if (i > 0) {
            return new ResourceLocation(path);
        } else if (i == 0) {
            path = path.substring(i + 1);
        }
        // only convert it to camel_case if it has any uppercase to begin with
        if (FormattingUtil.hasUpperCase(path)) {
            path = FormattingUtil.toLowerCaseUnderscore(path);
        }
        return TEMPLATE_LOCATION.withPath(path);
    }
}