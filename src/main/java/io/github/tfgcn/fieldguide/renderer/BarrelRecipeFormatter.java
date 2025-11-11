package io.github.tfgcn.fieldguide.renderer;

import io.github.tfgcn.fieldguide.Context;
import io.github.tfgcn.fieldguide.I18n;
import io.github.tfgcn.fieldguide.asset.FluidImageResult;
import io.github.tfgcn.fieldguide.asset.FluidLoader;
import io.github.tfgcn.fieldguide.asset.ItemImageResult;
import io.github.tfgcn.fieldguide.asset.ItemStackResult;
import io.github.tfgcn.fieldguide.exception.InternalException;

import java.util.List;
import java.util.Map;

/**
 * desc:
 *
 * @author yanmaoyuan
 */
public class BarrelRecipeFormatter {

    public static void formatBarrelRecipe(Context context, List<String> buffer, String identifier) {

        Map<String, Object> data = context.getLoader().loadRecipe(identifier);
        String recipeType = (String) data.get("type");

        if ("tfc:barrel_sealed".equals(recipeType)) {
            formatBarrelRecipeFromData(context, buffer, data);
        } else if ("tfc:barrel_instant".equals(recipeType)) {
            formatBarrelRecipeFromData(context, buffer, data);
        } else {
            throw new InternalException("Cannot handle barrel recipe type: " + recipeType);
        }

    }

    public static void formatBarrelRecipeFromData(Context context, List<String> buffer, Map<String, Object> data) {
        String inPath;
        String inName;
        String outPath;
        String outName;
        String fOutName;
        String fOutPath;
        String fInName;
        String fInPath;
        String inputFluidDiv = "";
        String inputItemDiv = "";
        String outputFluidDiv = "";
        String outputItemDiv = "";
        String duration = "";

        // 处理输入物品
        if (data.containsKey("input_item")) {
            Map<String, Object> inItem = (Map<String, Object>) data.get("input_item");
            Object ingredient = inItem.containsKey("ingredient") ? inItem.get("ingredient") : inItem;
            ItemImageResult ingredientResult = CraftingRecipeFormatter.formatIngredient(context, ingredient);
            inPath = ingredientResult.getPath();
            inName = ingredientResult.getName();
            int count = inItem.containsKey("count") ? ((Number) inItem.get("count")).intValue() : 1;
            inputItemDiv = makeIcon(inName, inPath, 1, CraftingRecipeFormatter.formatCount(count));
        }

        // 处理输出物品
        if (data.containsKey("output_item")) {
            ItemStackResult itemStack = CraftingRecipeFormatter.formatItemStack(context, data.get("output_item"));
            outPath = itemStack.path;
            outName = itemStack.name;
            int count = itemStack.count;
            outputItemDiv = makeIcon(outName, outPath, 3, CraftingRecipeFormatter.formatCount(count));
        }

        // 处理输入流体
        if (data.containsKey("input_fluid")) {
            FluidImageResult fluidResult = FluidLoader.getFluidImage(context, data.get("input_fluid"), true);
            fInPath = fluidResult.getPath();
            fInName = fluidResult.getName();
            inputFluidDiv = makeIcon(fInName, fInPath, 2, "");
        }

        // 处理输出流体
        if (data.containsKey("output_fluid")) {
            FluidImageResult fluidResult = FluidLoader.getFluidImage(context, data.get("output_fluid"), true);
            fOutPath = fluidResult.getPath();
            fOutName = fluidResult.getName();
            outputFluidDiv = makeIcon(fOutName, fOutPath, 4, "");
        }

        // 处理持续时间
        if (data.containsKey("duration")) {
            int durationTicks = ((Number) data.get("duration")).intValue();
            duration = String.format("""
            <div style="text-align: center;" class="minecraft-text minecraft-gray">
                <p>%s</p>
            </div>
            """, String.format(context.translate(I18n.TICKS), durationTicks));
        }

        String toAppend = String.format("""
    <div class="d-flex align-items-center justify-content-center">
        <div class="crafting-recipe">
            <img src="../../_images/2to2.png" />
            %s
            %s
            %s
            %s
            %s
        </div>
    </div>
    """, inputItemDiv, inputFluidDiv, outputItemDiv, outputFluidDiv, duration);

        buffer.add(toAppend);
    }

    public static String makeIcon(String name, String path, int index, String extraBit) {
        return String.format("""
        <div class="crafting-recipe-item two-recipe-pos-%d">
            <span href="#" data-bs-toggle="tooltip" title="%s" class="crafting-recipe-item-tooltip"></span>
            <img class="recipe-item" src="%s" />
            %s
        </div>
        """, index, name, path, extraBit == null ? "" : extraBit);
    }
}
