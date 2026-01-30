package team.terrafirmgreg.fieldguide.data.gtceu.utils;

import team.terrafirmgreg.fieldguide.asset.AssetLoader;
import team.terrafirmgreg.fieldguide.data.minecraft.ResourceLocation;

public class ResourceHelper {

    public static AssetLoader assetLoader;

    public static boolean isResourceExist(ResourceLocation rs) {
        return assetLoader.getAsset(String.format("assets/%s/%s", rs.getNamespace(), rs.getPath())) != null;
    }
}
