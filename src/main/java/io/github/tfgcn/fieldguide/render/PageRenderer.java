package io.github.tfgcn.fieldguide.render;

import io.github.tfgcn.fieldguide.asset.*;
import io.github.tfgcn.fieldguide.data.patchouli.BookEntry;
import io.github.tfgcn.fieldguide.data.patchouli.BookPage;
import io.github.tfgcn.fieldguide.data.patchouli.page.*;
import io.github.tfgcn.fieldguide.data.tfc.page.*;
import io.github.tfgcn.fieldguide.exception.InternalException;
import io.github.tfgcn.fieldguide.gson.JsonUtils;
import io.github.tfgcn.fieldguide.localization.I18n;
import io.github.tfgcn.fieldguide.localization.LocalizationManager;
import io.github.tfgcn.fieldguide.render.components.*;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

import static io.github.tfgcn.fieldguide.render.ImageTemplates.IMAGE_KNAPPING;
import static io.github.tfgcn.fieldguide.render.ImageTemplates.IMAGE_SINGLE;
import static io.github.tfgcn.fieldguide.render.TextureRenderer.resizeImage;

@Slf4j
public class PageRenderer {


    // 热量等级定义
    private static final List<HeatLevel> HEAT = Arrays.asList(
            new HeatLevel("warming", "gray", 80),
            new HeatLevel("hot", "gray", 210),
            new HeatLevel("very_hot", "gray", 480),
            new HeatLevel("faint_red", "dark-red", 580),
            new HeatLevel("dark_red", "dark-red", 730),
            new HeatLevel("bright_red", "red", 930),
            new HeatLevel("orange", "gold", 1100),
            new HeatLevel("yellow", "yellow", 1300),
            new HeatLevel("yellow_white", "yellow", 1400),
            new HeatLevel("white", "white", 1500),
            new HeatLevel("brilliant_white", "white", 1600)
    );

    // 玻璃加工物品映射
    private static final Map<String, String> GLASS_ITEMS = Map.ofEntries(
            Map.entry("saw", "tfc:gem_saw"),
            Map.entry("roll", "tfc:wool_cloth"),
            Map.entry("stretch", "tfc:blowpipe_with_glass"),
            Map.entry("blow", "tfc:blowpipe_with_glass"),
            Map.entry("table_pour", "tfc:blowpipe_with_glass"),
            Map.entry("basin_pour", "tfc:blowpipe_with_glass"),
            Map.entry("flatten", "tfc:paddle"),
            Map.entry("pinch", "tfc:jacks")
    );

    private final AssetLoader assetLoader;
    private final TextureRenderer textureRenderer;
    private final LocalizationManager localizationManager;

    private int id = 0;

    public PageRenderer(AssetLoader loader, LocalizationManager localizationManager, TextureRenderer textureRenderer) {
        this.assetLoader = loader;
        this.localizationManager = localizationManager;
        this.textureRenderer = textureRenderer;
    }

    public void renderPage(BookEntry entry, BookPage page) {
        String pageType = page.getType();
        String anchor = page.getAnchor();
        if (anchor != null) {
            entry.getBuffer().add(String.format("<a class=\"anchor\" id=\"%s\"></a>", anchor));
        }

        List<String> buffer = entry.getBuffer();
        switch (page) {
            case PageText pageText: {// patchouli:text
                formatTitle(entry, buffer, pageText.getTitle());
                formatText(entry, buffer, pageText.getText());
                break;
            }
            case PageImage pageImage: {// patchouli:image
                formatTitle(entry, buffer, pageImage.getTitle());
                renderImagePage(buffer, pageImage.getImages());
                formatCenteredText(entry, buffer, pageImage.getText());
                break;
            }
            case PageCrafting pageCrafting: {// patchouli:crafting
                formatTitle(entry, buffer, pageCrafting.getTitle());
                parseCraftingRecipe(buffer, pageCrafting);
                formatText(entry, buffer, pageCrafting.getText());
                break;
            }
            case PageSpotlight pageSpotlight: {// patchouli:spotlight
                parseSpotlightPage(entry, buffer, pageSpotlight);
                formatText(entry, buffer, pageSpotlight.getText());
                break;
            }
            case PageEntity pageEntity: {// patchouli:entity
                formatTitle(entry, buffer, pageEntity.getName());
                // TODO support entity
                formatText(entry, buffer, pageEntity.getText());
                break;
            }
            case PageEmpty ignored: {// patchouli:empty
                buffer.add("<hr>");
                break;
            }
            case PageMultiblock pageMultiblock: {// patchouli:multiblock
                formatTitle(entry, buffer, pageMultiblock.getName());
                parseMultiblockPage(buffer, pageMultiblock);
                formatCenteredText(entry, buffer, pageMultiblock.getText());
                break;
            }
            case PageMultiMultiblock pageMultiMultiblock: {// tfc:multimultiblock
                parseMultiMultiblockPage(buffer, pageMultiMultiblock);
                formatCenteredText(entry, buffer, pageMultiMultiblock.getText());
                break;
            }
            case PageHeating pageHeating: {// tfc:heating
                parseMiscRecipe(entry, buffer, pageHeating, pageType);
                formatText(entry, buffer, pageHeating.getText());
                break;
            }
            case PageQuern pageQuern:{// tfc:quern
                parseMiscRecipe(entry, buffer, pageQuern, pageType);
                formatText(entry, buffer, pageQuern.getText());
                break;
            }
            case PageLoom pageLoom: {// tfc:loom
                parseMiscRecipe(entry, buffer, pageLoom, pageType);
                formatText(entry, buffer, pageLoom.getText());
                break;
            }
            case PageAnvil pageAnvil: {// tfc:anvil
                parseMiscRecipe(entry, buffer, pageAnvil, pageType);
                formatText(entry, buffer, pageAnvil.getText());
                break;
            }
            case PageGlassworking pageGlassworking: {// tfc:glassworking
                parseMiscRecipe(entry, buffer, pageGlassworking, pageType);
                formatText(entry, buffer, pageGlassworking.getText());
                break;
            }
            case PageSmelting pageSmelting: {// tfc:smelting
                parseMiscRecipe(entry, buffer, pageSmelting, pageType);
                formatText(entry, buffer, pageSmelting.getText());
                break;
            }
            case PageDrying pageDrying: {// tfc:drying
                parseMiscRecipe(entry, buffer, pageDrying, pageType);
                formatText(entry, buffer, pageDrying.getText());
                break;
            }
            case PageBarrel pageBarrel: {// tfc:instant_barrel_recipe, tfc:sealed_barrel_recipe
                parseBarrelRecipe(buffer, pageBarrel, pageType);
                break;
            }
            case PageWelding pageWelding: {// tfc:welding_recipe
                formatRecipe(buffer, pageWelding.getRecipe());
                // TODO
                formatText(entry, buffer, pageWelding.getText());
                break;
            }
            case PageRockKnapping pageRockKnapping: {// tfc:rock_knapping_recipe, tfc:clay_knapping_recipe, tfc:fire_clay_knapping_recipe, tfc:leather_knapping_recipe
                parseRockKnappingRecipe(buffer, pageRockKnapping);
                formatText(entry, buffer, pageRockKnapping.getText());
                break;
            }
            case PageKnapping pageKnapping: {// tfc:knapping
                parseKnappingRecipe(buffer, pageKnapping);
                formatText(entry, buffer, pageKnapping.getText());
                break;
            }
            case PageTable pageTable: {// tfc:table, tfc:table_small
                parseTablePage(entry, buffer, pageTable);
                break;
            }
            default: {
                log.warn("Unrecognized page type: {}, {}", pageType, page);
            }
        }
    }


    public void formatText(BookEntry entry, List<String> buffer, String text) {
        if (text != null && !text.isEmpty()) {
            TextFormatter.formatText(buffer, text, localizationManager.getKeybindings());

            entry.addSearchContent(TextFormatter.searchStrip(text));
        }
    }

    public void formatTitle(BookEntry entry, List<String> buffer, String title) {
        if (title != null && !title.isEmpty()) {
            String stripped = TextFormatter.stripVanillaFormatting(title);
            buffer.add("<h5>" + stripped + "</h5>\n");

            entry.addSearchContent(stripped);
        }
    }

    /**
     * 带图标的标题格式化
     */
    public void formatTitleWithIcon(BookEntry entry, List<String> buffer, String iconSrc, String iconName,
                                    String inTitle, String tag,
                                    String tooltip) {
        String title = iconName;
        if (inTitle != null && !inTitle.isEmpty()) {
            title = TextFormatter.stripVanillaFormatting(inTitle);
            if (iconName == null || iconName.isEmpty()) {
                iconName = title;
            }

            entry.addSearchContent(iconName);
        }

        if (tooltip == null) {
            tooltip = title;
        }

        String html = String.format("""
            <div class="item-header">
                <span href="#" data-bs-toggle="tooltip" title="%s">
                    <img src="../../%s" alt="%s" />
                </span>
                <%s>%s</%s>
            </div>
            """, iconName, iconSrc, tooltip, tag, title, tag);

        buffer.add(html);
    }

    public void formatTitleWithIcon(BookEntry entry, List<String> buffer, String iconSrc, String iconName, String title) {
        formatTitleWithIcon(entry, buffer, iconSrc, iconName, title, "h5", null);
    }

    /**
     * 居中对齐文本
     */
    public void formatCenteredText(BookEntry entry, List<String> buffer, String text) {
        buffer.add("<div style=\"text-align: center;\">");
        formatText(entry, buffer, text);
        buffer.add("</div>");
    }

    /**
     * 带提示的文本
     */
    public void formatWithTooltip(List<String> buffer, String text, String tooltip) {
        String html = String.format("""
            <div style="text-align: center;">
                <p class="text-muted"><span href="#" data-bs-toggle="tooltip" title="%s">%s</span></p>
            </div>
            """, tooltip, text);
        buffer.add(html);
    }

    /**
     * 格式化配方
     */
    public void formatRecipe(List<String> buffer, String recipeId) {
        if (recipeId != null && !recipeId.isEmpty()) {
            String text = String.format("%s: <code>%s</code>", localizationManager.translate(I18n.RECIPE), recipeId);
            formatWithTooltip(buffer, text, localizationManager.translate(I18n.RECIPE_ONLY_IN_GAME));
        }
    }

    ///  patchouli:image
    private void renderImagePage(List<String> buffer, List<String> images) {
        List<Map.Entry<String, String>> processedImages = new ArrayList<>();

        if (images != null) {
            for (String image : images) {
                try {
                    String convertedImage = textureRenderer.convertImage(image);
                    processedImages.add(Map.entry(image, convertedImage));
                } catch (InternalException e) {
                    log.error("Failed to convert entry image: {}", image, e);
                }
            }
        }

        if (processedImages.size() == 1) {
            Map.Entry<String, String> imageEntry = processedImages.getFirst();
            buffer.add(String.format(IMAGE_SINGLE,
                    imageEntry.getValue(), imageEntry.getKey()));
        } else if (!processedImages.isEmpty()) {
            String uid = String.valueOf(id++);
            StringBuilder parts = new StringBuilder();
            StringBuilder seq = new StringBuilder();

            for (int i = 0; i < processedImages.size(); i++) {
                Map.Entry<String, String> imageEntry = processedImages.get(i);
                String active = i == 0 ? "active" : "";
                parts.append(String.format(ImageTemplates.IMAGE_MULTIPLE_PART, active, imageEntry.getValue(), imageEntry.getKey()));

                if (i > 0) {
                    seq.append(String.format(ImageTemplates.IMAGE_MULTIPLE_SEQ, uid, i, i + 1));
                }
            }

            buffer.add(MessageFormat.format(ImageTemplates.IMAGE_MULTIPLE, uid, seq.toString(), parts.toString()));
        }
    }
    /// crafting recipe

    private void parseCraftingRecipe(List<String> buffer, PageCrafting page) {
        // 处理主要配方
        if (page.getRecipe() != null) {
            try {
                formatCraftingRecipe(buffer, page.getRecipe());
            } catch (Exception e) {
                // TODO add "e" later
                log.error("Recipe processing craft failed: {}. e: {}", page.getRecipe(), e.getMessage());
                formatRecipe(buffer, page.getRecipe());
            }
        }

        // 处理第二个配方
        if (page.getRecipe2() != null) {
            try {
                formatCraftingRecipe(buffer, page.getRecipe2());
            } catch (Exception e) {
                // TODO add e later
                log.error("Recipe2 processing failed: {}, message: {}", page.getRecipe2(), e.getMessage());
                formatRecipe(buffer, page.getRecipe2());
            }
        }
    }


    /**
     * 格式化合成配方
     */
    public void formatCraftingRecipe(List<String> buffer, String identifier) {
        Map<String, Object> recipe = assetLoader.loadRecipe(identifier);
        formatCraftingRecipeFromData(buffer, identifier, recipe);
    }

    /**
     * 从数据格式化合成配方
     */
    private void formatCraftingRecipeFromData(List<String> buffer, String identifier, Map<String, Object> data) {
        String recipeType = (String) data.get("type");
        CraftingRecipe recipe;

        switch (recipeType) {
            case "minecraft:crafting_shaped":
                recipe = parseShapedRecipe(data);
                break;

            case "minecraft:crafting_shapeless": {
                recipe = parseShapelessRecipe(data);
                break;
            }
            case "waterflasks:heal_flask": {
                Map<String, Object> innerRecipe = (Map<String, Object>) data.get("recipe");
                String type = (String) innerRecipe.get("type");
                if ("minecraft:crafting_shaped".equals(type)) {
                    recipe = parseShapedRecipe(innerRecipe);
                } else if ("minecraft:crafting_shapless".equals(type)) {
                    recipe = parseShapelessRecipe(innerRecipe);
                } else {
                    throw new InternalException("Unsupported recipe: " + type + " of " + identifier);
                }
                break;
            }
            case "tfc:damage_inputs_shaped_crafting":
            case "tfc:damage_inputs_shapeless_crafting":
            case "tfc:extra_products_shapeless_crafting":
            case "tfc:no_remainder_shapeless_crafting": {
                Map<String, Object> innerRecipe = (Map<String, Object>) data.get("recipe");
                formatCraftingRecipeFromData(buffer, identifier, innerRecipe);
                return;
            }
            case "tfc:advanced_shaped_crafting": {
                data.put("type", "minecraft:crafting_shaped");
                Object result = data.get("result");
                Object stack = anyOf(result, "stack", "id");
                if (stack == null) {
                    throw new InternalException("Advanced shaped crafting with complex modifiers: '" + data.get("result") + "'");
                }
                data.put("result", stack); // 丢弃修饰符
                formatCraftingRecipeFromData(buffer, identifier, data);
                return;
            }
            case "tfc:advanced_shapeless_crafting": {
                data.put("type", "minecraft:crafting_shapeless");
                Object result2 = data.get("result");
                Object stack2 = anyOf(result2, "stack", "id");
                if (stack2 == null) {
                    throw new InternalException("Advanced shapeless crafting with complex modifiers: '" + data.get("result") + "'");
                }
                data.put("result", stack2); // 丢弃修饰符
                formatCraftingRecipeFromData(buffer, identifier, data);
                return;
            }
            default:
                throw new RuntimeException("Unknown crafting recipe type: " + recipeType + " for recipe " + identifier);
        }

        for (int i = 0; i < recipe.grid.length; i++) {
            Object key = recipe.grid[i];
            if (key != null) {
                recipe.grid[i] = formatIngredient(key);
            }
        }

        buffer.add(String.format("""
            <div class="d-flex align-items-center justify-content-center">
                <div class="crafting-recipe">
                    <img src="../../_images/crafting_%s.png" />
            """, recipe.shapeless ? "shapeless" : "shaped"));

        // 添加网格物品
        for (int i = 0; i < recipe.grid.length; i++) {
            Object key = recipe.grid[i];
            if (key != null) {
                ItemImageResult ingredient = (ItemImageResult) key;
                int x = i % 3;
                int y = i / 3;
                buffer.add(String.format("""
                    <div class="crafting-recipe-item crafting-recipe-pos-%d-%d">
                        <span href="#" data-bs-toggle="tooltip" title="%s" class="crafting-recipe-item-tooltip"></span>
                        <img class="recipe-item" src="../../%s" />
                    </div>
                    """, x, y, ingredient.getName(), ingredient.getPath()));
            }
        }

        // 添加输出物品
        ItemStackResult output = recipe.output;
        buffer.add(String.format("""
                    <div class="crafting-recipe-item crafting-recipe-pos-out">
                        <span href="#" data-bs-toggle="tooltip" title="%s" class="crafting-recipe-item-tooltip"></span>
                        %s
                        <img class="recipe-item" src="../../%s" />
                    </div>
                </div>
            </div>
            """,
                output.name,
                formatCount(output.count),
                output.path
        ));
    }

    /**
     * 从多个键中获取第一个存在的值
     */
    public static Object anyOf(Object data, String... keys) {
        if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            for (String key : keys) {
                if (map.containsKey(key)) {
                    return map.get(key);
                }
            }
        }
        return null;
    }

    /**
     * 解析有序合成配方
     */
    private CraftingRecipe parseShapedRecipe(Map<String, Object> data) {
        CraftingRecipe recipe = new CraftingRecipe();
        List<String> pattern = (List<String>) data.get("pattern");
        Map<String, Object> keyMap = (Map<String, Object>) data.get("key");

        for (int y = 0; y < pattern.size(); y++) {
            String row = pattern.get(y);
            for (int x = 0; x < row.length(); x++) {
                char keyChar = row.charAt(x);
                if (keyChar != ' ') {
                    recipe.grid[x + 3 * y] = keyMap.get(String.valueOf(keyChar));
                }
            }
        }

        recipe.output = formatItemStack(data.get("result"));
        recipe.shapeless = false;
        return recipe;
    }

    /**
     * 解析无序合成配方
     */
    private CraftingRecipe parseShapelessRecipe(Map<String, Object> data) {
        CraftingRecipe recipe = new CraftingRecipe();
        List<Object> ingredients = (List<Object>) data.get("ingredients");

        for (int i = 0; i < ingredients.size(); i++) {
            recipe.grid[i] = ingredients.get(i);
        }

        recipe.output = formatItemStack(data.get("result"));
        recipe.shapeless = true;
        return recipe;
    }

    /**
     * 格式化成分
     */
    public ItemImageResult formatIngredient(Object data) {
        if (data instanceof Map) {
            Map<String, Object> mapData = (Map<String, Object>) data;

            if (mapData.containsKey("item")) {
                return textureRenderer.getItemImage((String) mapData.get("item"), true);
            } else if (mapData.containsKey("tag")) {
                return textureRenderer.getItemImage("#" + mapData.get("tag"), true);
            } else if (mapData.containsKey("type")) {
                String type = (String) mapData.get("type");
                switch (type) {
                    case "tfc:has_trait": {// FIXME 不知道对不对。这是 firmalife:food/pineapple firmalife:dried
                        return formatIngredient(mapData.get("ingredient"));
                    }
                    case "tfc:lacks_trait": {// FIXME 不知道对不对。这是 casting_channel 做巧克力的配方。
                        return formatIngredient(mapData.get("ingredient"));
                    }
                    case "tfc:not_rotten":
                        return formatIngredient(mapData.get("ingredient"));
                    case "tfc:fluid_item":
                        Map<String, Object> fluidIngredient = (Map<String, Object>) mapData.get("fluid_ingredient");
                        Map<String, Object> ingredient = (Map<String, Object>) fluidIngredient.get("ingredient");
                        if (!"minecraft:water".equals(ingredient.get("ingredient"))) {
                            throw new RuntimeException("Unknown `tfc:fluid_item` ingredient: '" + data + "'");
                        }
                        return textureRenderer.getItemImage("minecraft:water_bucket", true);
                    case "tfc:fluid_content":
                        Map<String, Object> fluid = (Map<String, Object>) mapData.get("fluid");
                        if (!"minecraft:water".equals(fluid.get("fluid"))) {
                            throw new RuntimeException("Unknown `tfc:fluid_content` ingredient: '" + data + "'");
                        }
                        return textureRenderer.getItemImage("minecraft:water_bucket", true);
                    case "tfc:and":
                        List<Map<String, Object>> children = (List<Map<String, Object>>) mapData.get("children");
                        StringBuilder csvString = new StringBuilder();
                        for (Map<String, Object> child : children) {
                            if (child.containsKey("item")) {
                                if (!csvString.isEmpty()) {
                                    csvString.append(",");
                                }
                                csvString.append(child.get("item"));
                            }
                        }
                        return textureRenderer.getItemImage(csvString.toString(), true);
                    default:
                        log.info("Unknown ingredient type: {}", type);
                }
            }
        } else if (data instanceof List) {
            List<Object> listData = (List<Object>) data;
            StringBuilder csvString = new StringBuilder();
            for (Object item : listData) {
                if (item instanceof Map) {
                    Map<String, Object> itemMap = (Map<String, Object>) item;
                    if (itemMap.containsKey("item")) {
                        if (!csvString.isEmpty()) {
                            csvString.append(",");
                        }
                        csvString.append(itemMap.get("item"));
                    }
                }
            }
            return textureRenderer.getItemImage(csvString.toString(), true);
        }

        throw new RuntimeException("Unsupported ingredient: " + data);
    }

    /**
     * 格式化带数量的成分
     */
    public SizedIngredientResult formatSizedIngredient(Object data) {
        ItemImageResult ingredient = formatIngredient(data);
        int count = 1;
        if (data instanceof Map) {
            Map<String, Object> mapData = (Map<String, Object>) data;
            if (mapData.containsKey("count")) {
                count = ((Number) mapData.get("count")).intValue();
            }
        }
        return new SizedIngredientResult(ingredient, count);
    }

    /**
     * 格式化物品堆
     */
    public ItemStackResult formatItemStack(Object data) {
        if (data instanceof Map) {
            Map<String, Object> mapData = (Map<String, Object>) data;
            if (mapData.containsKey("modifiers") && mapData.containsKey("stack")) {
                return formatItemStack(mapData.get("stack")); // 丢弃修饰符
            }

            String itemId = null;
            if (mapData.containsKey("item")) {
                itemId = (String) mapData.get("item");
            } else if (mapData.containsKey("id")) {
                itemId = (String) mapData.get("id");
            }

            if (itemId != null) {
                ItemImageResult itemImage = textureRenderer.getItemImage(itemId, true);
                int count = mapData.containsKey("count") ? ((Number) mapData.get("count")).intValue() : 1;
                return new ItemStackResult(itemImage.getPath(), itemImage.getName(), count);
            }
        }

        // 默认返回占位符
        return new ItemStackResult("../../_images/placeholder_64.png", null, 1);
    }

    /**
     * 格式化数量显示
     */
    public static String formatCount(int count) {
        return count > 1 ?
                String.format("<p class=\"crafting-recipe-item-count\">%d</p>", count) :
                "";
    }

    ///  spotlight
    private void parseSpotlightPage(BookEntry entry, List<String> buffer, PageSpotlight page) {
        List<PageSpotlightItem> items = page.getItem();
        if (items == null || items.isEmpty()) {
            log.warn("Spotlight page did not have an item or tag key: {}", page);
            return;
        }
        try {
            for (PageSpotlightItem item : items) {
                if ("tag".equals(item.getType())) {
                    ItemImageResult itemResult = textureRenderer.getItemImage("#" + item.getText(), false);
                    formatTitleWithIcon(entry, buffer, itemResult.getPath(), itemResult.getName(), page.getTitle());
                } else {
                    ItemImageResult itemResult = textureRenderer.getItemImage(item.getText(), false);
                    formatTitleWithIcon(entry, buffer, itemResult.getPath(), itemResult.getName(), page.getTitle());
                }
            }
        } catch (Exception e) {
            // Fallback
            formatTitle(entry, buffer, page.getTitle());

            int count = 0;
            StringBuilder sb = new StringBuilder();
            for (PageSpotlightItem item : items) {
                if (count > 0) {
                    sb.append(", ");
                }
                sb.append("<code>");
                if ("tag".equals(item.getType())) {
                    sb.append('#').append(item.getText());
                } else {
                    sb.append(item.getText());
                }
                sb.append("</code>");
                count++;
            }
            String itemHtml = String.format("%s: %s", localizationManager.translate(count > 1 ? I18n.ITEMS : I18n.ITEM), sb);
            formatWithTooltip(buffer, itemHtml, localizationManager.translate(I18n.ITEM_ONLY_IN_GAME));
        }
    }

    private void parseMultiblockPage(List<String> buffer, PageMultiblock page) {
        try {
            String src = textureRenderer.getMultiBlockImage(page);
            buffer.add(String.format(IMAGE_SINGLE, src, "Block Visualization"));
        } catch (Exception e) {
            // FIXME add me later log.error("Multiblock image processing failed, message: {}", e.getMessage());
            // Fallback
            if (page.getMultiblockId() != null) {
                formatWithTooltip(buffer,
                        String.format("%s: <code>%s</code>", localizationManager.translate(I18n.MULTIBLOCK), page.getMultiblockId()),
                        localizationManager.translate(I18n.MULTIBLOCK_ONLY_IN_GAME));
            } else {
                // FIXME for debug
                formatWithTooltip(buffer,
                        String.format("%s: <code>%s</code>", localizationManager.translate(I18n.MULTIBLOCK), JsonUtils.toJson(page.getMultiblock())),
                        localizationManager.translate(I18n.MULTIBLOCK_ONLY_IN_GAME));
            }
        }
    }

    private void parseMultiMultiblockPage(List<String> buffer, PageMultiMultiblock page) {
        try {
            String src = textureRenderer.getMultiBlockImage(page);
            buffer.add(String.format(IMAGE_SINGLE, src, "Block Visualization"));
        } catch (Exception e) {
            // TODO 日志太多暂时移除 log.error("tfc:multimultiblock image processing failed", e);
            // FIXME for debug
            for (TFCMultiblockData multiblock : page.getMultiblocks()) {
                formatWithTooltip(buffer,
                        String.format("%s: <code>%s</code>", localizationManager.translate(I18n.MULTIBLOCK), JsonUtils.toJson(multiblock)),
                        localizationManager.translate(I18n.MULTIBLOCK_ONLY_IN_GAME));
            }
            // TODO formatWithTooltip(buffer, translate(I18n.MULTIBLOCK), translate(I18n.MULTIBLOCK_ONLY_IN_GAME));
        }
    }

    ///  misc recipe
    private void parseMiscRecipe(BookEntry entry, List<String> buffer, IPageDoubleRecipe page, String pageType) {
        try {
            formatMiscRecipe(entry, buffer, page.getRecipe());
        } catch (Exception e) {
            // TODO add e later
            log.error("Misc recipe processing failed: {}, message: {}", pageType, e.getMessage());
            formatRecipe(buffer, page.getRecipe());
        }
    }

    /**
     * 格式化杂项配方
     */
    public void formatMiscRecipe(BookEntry entry, List<String> buffer, String identifier) {
        Map<String, Object> data = assetLoader.loadRecipe(identifier);
        String recipeType = (String) data.get("type");

        switch (recipeType) {
            case "tfc:quern":
                formatMiscRecipeFromData(buffer, identifier, data);
                break;

            case "tfc:heating":
                formatMiscRecipeFromData(buffer, identifier, data, "result_item");
                TemperatureResult tempResult = getTemperature(((Number) data.get("temperature")).intValue());
                buffer.add(String.format("""
                    <div style="text-align: center;" class="minecraft-text minecraft-%s">
                        <p>%s</p>
                    </div>
                    """, tempResult.cssClass, tempResult.tooltip));
                break;

            case "tfc:loom":
                handleLoomRecipe(buffer, identifier, data);
                break;

            case "tfc:anvil":
                handleAnvilRecipe(buffer, identifier, data);
                break;

            case "tfc:glassworking":
                formatGlassworkingRecipe(entry, buffer, identifier, data);
                break;

            default:
                throw new RuntimeException("Cannot handle as a misc recipe: " + recipeType);
        }
    }

    /**
     * 处理织布机配方
     */
    private void handleLoomRecipe(List<String> buffer, String identifier, Map<String, Object> data) {
        int count = 1;
        Object ingredient = null;

        if (data.containsKey("ingredient")) {
            Object ing = data.get("ingredient");
            if (ing instanceof Map) {
                Map<String, Object> ingMap = (Map<String, Object>) ing;
                if (data.containsKey("input_count")) {
                    // 1.18 格式
                    count = ((Number) data.get("input_count")).intValue();
                    ingredient = ing;
                } else if (ingMap.containsKey("ingredient") && ingMap.containsKey("count")) {
                    // 嵌套格式
                    ingredient = ingMap.get("ingredient");
                    count = ((Number) ingMap.get("count")).intValue();
                } else if (ingMap.containsKey("count")) {
                    // 1.20 格式
                    count = ((Number) ingMap.get("count")).intValue();
                    ingredient = ing;
                }
            }
        }

        if (ingredient != null) {
            formatMiscRecipeFromData(buffer, identifier, data, ingredient, count);
        } else {
            throw new RuntimeException("Unrecognized loom recipe format: " + data);
        }
    }

    /**
     * 处理铁砧配方
     */
    private void handleAnvilRecipe(List<String> buffer, String identifier, Map<String, Object> data) {
        Object ingredient = null;

        if (data.containsKey("input")) {
            ingredient = data.get("input");
        } else if (data.containsKey("ingredient")) {
            ingredient = data.get("ingredient");
        }

        if (ingredient != null) {
            formatMiscRecipeFromData(buffer, identifier, data, ingredient, 1);
            String tooltip = getTier(((Number) data.get("tier")).intValue());
            buffer.add(String.format("""
                <div style="text-align: center;" class="minecraft-text minecraft-gray">
                    <p>%s</p>
                </div>
                """, tooltip));
        } else {
            throw new RuntimeException("Unrecognized anvil recipe format: " + data);
        }
    }

    /**
     * 处理玻璃加工配方
     */
    private void formatGlassworkingRecipe(BookEntry entry, List<String> buffer, String identifier, Map<String, Object> data) {
        formatMiscRecipeFromData(buffer, identifier, data, data.get("batch"), 1);
        buffer.add("<h4>Steps</h4><ol>");

        List<String> operations = (List<String>) data.get("operations");
        for (String key : operations) {
            String opName = localizationManager.translate("tfc.enum.glassoperation." + key);

            if (!GLASS_ITEMS.containsKey(key)) {
                throw new RuntimeException("Missing item for glass op: " + key);
            }

            String opItem = GLASS_ITEMS.get(key);
            try {
                ItemImageResult itemResult = textureRenderer.getItemImage(opItem, false);
                buffer.add("<li>");
                formatTitleWithIcon(entry, buffer, itemResult.getPath(), opName, (String)data.get( "title"), "p", itemResult.getName());
                buffer.add("</li>");
            } catch (Exception e) {
                System.err.println("Warning: " + e.getMessage());
                buffer.add("<li><p>" + opName + "</p></li>");
            }
        }

        buffer.add("</ol>");
    }

    /**
     * 从数据格式化杂项配方
     */
    public void formatMiscRecipeFromData(List<String> buffer, String identifier, Map<String, Object> data) {
        formatMiscRecipeFromData(buffer, identifier, data, null, "result", 1);
    }

    public void formatMiscRecipeFromData(List<String> buffer, String identifier, Map<String, Object> data, String resultField) {
        formatMiscRecipeFromData(buffer, identifier, data, null, resultField, 1);
    }

    public void formatMiscRecipeFromData(List<String> buffer, String identifier, Map<String, Object> data, Object ingredient, int inCount) {
        formatMiscRecipeFromData(buffer, identifier, data, ingredient, "result", inCount);
    }

    public void formatMiscRecipeFromData(List<String> buffer, String identifier, Map<String, Object> data, Object ingredient, String resultField, int inCount) {
        if (!data.containsKey(resultField)) {
            throw new RuntimeException("Missing '" + resultField + "' field for recipe: " + identifier);
        }

        if (ingredient == null) {
            ingredient = data.get("ingredient");
        }

        ItemImageResult inResult = formatIngredient(ingredient);
        ItemStackResult outResult = formatItemStack(data.get(resultField));

        buffer.add(String.format("""
            <div class="d-flex align-items-center justify-content-center">
                <div class="crafting-recipe">
                    <img src="../../_images/1to1.png" />
                    <div class="crafting-recipe-item misc-recipe-pos-in">
                        <span href="#" data-bs-toggle="tooltip" title="%s" class="crafting-recipe-item-tooltip"></span>
                        %s
                        <img class="recipe-item" src="../../%s" />
                    </div>
                    <div class="crafting-recipe-item misc-recipe-pos-out">
                        <span href="#" data-bs-toggle="tooltip" title="%s" class="crafting-recipe-item-tooltip"></span>
                        %s
                        <img class="recipe-item" src="../../%s" />
                    </div>
                </div>
            </div>
            """,
                inResult.getName(),
                CraftingRecipeFormatter.formatCount(inCount),
                inResult.getPath(),
                outResult.getName(),
                CraftingRecipeFormatter.formatCount(outResult.getCount()),
                outResult.getPath()
        ));
    }

    /**
     * 获取温度信息
     */
    public TemperatureResult getTemperature(int temperature) {
        for (int i = 0; i < HEAT.size() - 1; i++) {
            HeatLevel current = HEAT.get(i);
            HeatLevel next = HEAT.get(i + 1);

            if (temperature <= current.value) {
                String tooltip = localizationManager.translate("tfc.enum.heat." + current.key);

                // 计算温度等级内的细分
                for (double t : Arrays.asList(0.2, 0.4, 0.6, 0.8)) {
                    if (temperature < current.value + (next.value - current.value) * t) {
                        tooltip += "*";
                    }
                }

                return new TemperatureResult(current.cssClass, tooltip);
            }
        }

        HeatLevel last = HEAT.get(HEAT.size() - 1);
        return new TemperatureResult(last.cssClass, localizationManager.translate("tfc.enum.heat." + last.key));
    }

    /**
     * 获取等级信息
     */
    public String getTier(int tier) {
        String[] tierNames = {"0", "i", "ii", "iii", "iv", "v", "vi", "vii"};
        if (tier >= 0 && tier < tierNames.length) {
            return localizationManager.translate("tfc.enum.tier.tier_" + tierNames[tier]);
        }
        return localizationManager.translate("tfc.enum.tier.tier_0");
    }

    // 辅助类
    public static class HeatLevel {
        public final String key;
        public final String cssClass;
        public final int value;

        public HeatLevel(String key, String cssClass, int value) {
            this.key = key;
            this.cssClass = cssClass;
            this.value = value;
        }
    }

    public static class TemperatureResult {
        public final String cssClass;
        public final String tooltip;

        public TemperatureResult(String cssClass, String tooltip) {
            this.cssClass = cssClass;
            this.tooltip = tooltip;
        }
    }

    /// barrel recipe
    private void parseBarrelRecipe(List<String> buffer, PageBarrel page, String pageType) {
        try {
            formatBarrelRecipe(buffer, page.getRecipe());
        } catch (Exception e) {
            log.error("Barrel recipe processing failed: {}", pageType, e);
            formatRecipe(buffer, page.getRecipe());
        }
    }

    private void formatBarrelRecipe(List<String> buffer, String identifier) {

        Map<String, Object> data = assetLoader.loadRecipe(identifier);
        String recipeType = (String) data.get("type");

        if ("tfc:barrel_sealed".equals(recipeType)) {
            formatBarrelRecipeFromData(buffer, data);
        } else if ("tfc:barrel_instant".equals(recipeType)) {
            formatBarrelRecipeFromData(buffer, data);
        } else {
            throw new InternalException("Cannot handle barrel recipe type: " + recipeType);
        }

    }

    private void formatBarrelRecipeFromData(List<String> buffer, Map<String, Object> data) {
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
            ItemImageResult ingredientResult = formatIngredient(ingredient);
            inPath = ingredientResult.getPath();
            inName = ingredientResult.getName();
            int count = inItem.containsKey("count") ? ((Number) inItem.get("count")).intValue() : 1;
            inputItemDiv = makeIcon(inName, inPath, 1, CraftingRecipeFormatter.formatCount(count));
        }

        // 处理输出物品
        if (data.containsKey("output_item")) {
            ItemStackResult itemStack = formatItemStack(data.get("output_item"));
            outPath = itemStack.path;
            outName = itemStack.name;
            int count = itemStack.count;
            outputItemDiv = makeIcon(outName, outPath, 3, formatCount(count));
        }

        // 处理输入流体
        if (data.containsKey("input_fluid")) {
            FluidImageResult fluidResult = textureRenderer.getFluidImage(data.get("input_fluid"), true);
            fInPath = fluidResult.getPath();
            fInName = fluidResult.getName();
            inputFluidDiv = makeIcon(fInName, fInPath, 2, "");
        }

        // 处理输出流体
        if (data.containsKey("output_fluid")) {
            FluidImageResult fluidResult = textureRenderer.getFluidImage(data.get("output_fluid"), true);
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
            """, String.format(localizationManager.translate(I18n.TICKS), durationTicks));
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
            <img class="recipe-item" src="../../%s" />
            %s
        </div>
        """, index, name, path, extraBit == null ? "" : extraBit);
    }

    /// knapping recipe
    private void parseRockKnappingRecipe(List<String> buffer, PageRockKnapping page) {
        try {
            String recipeId = page.getRecipes().getFirst();
            KnappingRecipe recipe = formatKnappingRecipe(recipeId);
            buffer.add(String.format(IMAGE_KNAPPING, recipe.image(), "Recipe: " + recipeId));
        } catch (Exception e) {
            // TODO add e later
            log.error("Failed to load knapping page: {}, message: {}", page.getRecipes(), e.getMessage());
            formatRecipe(buffer, page.getRecipes().getFirst());
        }
    }

    /// knapping recipe
    private void parseKnappingRecipe(List<String> buffer, PageKnapping page) {
        try {
            KnappingRecipe recipe = formatKnappingRecipe(page.getRecipe());
            buffer.add(String.format(IMAGE_KNAPPING, recipe.image(), "Recipe: " + recipe.recipeId()));
        } catch (Exception e) {
            log.error("Failed to load knapping page: {}", page, e);
            formatRecipe(buffer, page.getRecipe());
        }
    }


    // 敲击类型常量
    public static final List<KnappingType> KNAPPING_TYPES = Arrays.asList(
            new KnappingType("tfc:rock_knapping", "tfc:rock", "tfc:textures/gui/knapping/rock/loose/granite.png", null),
            new KnappingType("tfc:clay_knapping", "tfc:clay", "tfc:textures/gui/knapping/clay_ball.png", "tfc:textures/gui/knapping/clay_ball_disabled.png"),
            new KnappingType("tfc:fire_clay_knapping", "tfc:fire_clay", "tfc:textures/gui/knapping/fire_clay.png", "tfc:textures/gui/knapping/fire_clay_disabled.png"),
            new KnappingType("tfc:leather_knapping", "tfc:leather", "tfc:textures/gui/knapping/leather.png", null),
            new KnappingType(null, "bsa:bone", "tfc:textures/gui/knapping/bone.png", null),
            new KnappingType(null, "bsa:sherd", "tfc:textures/gui/knapping/ceramic/sherd/unfired/blank.png", "tfc:textures/gui/knapping/ceramic/sherd/unfired/blank_disabled.png")
    );

    public static final String KNAPPING_RECIPE_OUTLINE = "tfc:textures/gui/book/icons.png";
    private static final Map<String, KnappingRecipe> KNAPPING_RECIPE_CACHE = new HashMap<>();

    /**
     * 格式化敲击配方
     * @param recipeId 配方ID
     * @return 包含配方ID和图片路径的数组
     */
    private KnappingRecipe formatKnappingRecipe(String recipeId) {
        if (KNAPPING_RECIPE_CACHE.containsKey(recipeId)) {
            return KNAPPING_RECIPE_CACHE.get(recipeId);
        }

        Map<String, Object> recipeData = assetLoader.loadRecipe(recipeId);
        BufferedImage img = new BufferedImage(90, 90, BufferedImage.TYPE_INT_ARGB);

        // 1.18版本使用'type'字段表示敲击类型
        // 1.20版本'type'字段仅为'tfc:knapping'，使用'knapping_type'字段表示具体类型
        KnappingType typeData;
        if (recipeData.containsKey("knapping_type")) {
            String knappingType = (String) recipeData.get("knapping_type");
            typeData = KNAPPING_TYPES.stream()
                    .filter(t -> knappingType.equals(t.type_1_20()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Unknown knapping type: " + knappingType));
        } else {
            String type = (String) recipeData.get("type");
            typeData = KNAPPING_TYPES.stream()
                    .filter(t -> type.equals(t.type_1_18()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Unknown knapping type: " + type));
        }

        BufferedImage low = null;
        BufferedImage hi = null;

        if (typeData.low() != null) {
            low = assetLoader.loadTexture(typeData.low());
            if (low.getWidth() != 16 || low.getHeight() != 16) {
                low = resizeImage(low, 16, 16);
            }
        }
        if (typeData.hi() != null) {
            hi = assetLoader.loadTexture(typeData.hi());
            if (hi.getWidth() != 16 || hi.getHeight() != 16) {
                hi = resizeImage(hi, 16, 16);
            }
        }

        // 图案
        List<String> pattern = (List<String>) recipeData.get("pattern");
        boolean outsideSlot = recipeData.containsKey("outside_slot_required") ?
                (Boolean) recipeData.get("outside_slot_required") : true;

        // 如果图案在任何方向上小于5格宽，我们偏移它以使其居中显示，向下取整
        int offsetY = (5 - pattern.size()) / 2;
        int offsetX = (5 - pattern.getFirst().length()) / 2;

        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                if (0 <= y - offsetY && y - offsetY < pattern.size() &&
                        0 <= x - offsetX && x - offsetX < pattern.get(y - offsetY).length()) {
                    // 在边界内
                    char patternChar = pattern.get(y - offsetY).charAt(x - offsetX);
                    BufferedImage tile = (patternChar == ' ') ? hi : low;
                    if (tile != null) {
                        // 在Java中绘制图片到指定位置
                        img.getGraphics().drawImage(tile, 5 + 16 * x, 5 + 16 * y, null);
                    }
                } else {
                    // 超出边界
                    BufferedImage tile = outsideSlot ? low : hi;
                    if (tile != null) {
                        img.getGraphics().drawImage(tile, 5 + 16 * x, 5 + 16 * y, null);
                    }
                }
            }
        }

        AssetKey assetKey = new AssetKey(recipeId, "textures/recipes", "assets", ".png");
        String path = textureRenderer.saveImage(assetKey.getResourcePath(), img);
        KnappingRecipe result = new KnappingRecipe(recipeId, path);
        KNAPPING_RECIPE_CACHE.put(recipeId, result);
        return result;
    }

    /// page_table
    private void parseTablePage(BookEntry entry, List<String> buffer, PageTable page) {
        try {
            formatTable(entry, buffer, page);
        } catch (InternalException e) {
            log.error("Table formatting failed", e);
        }
    }


    private void formatTable(BookEntry entry, List<String> buffer, PageTable data) {
        List<PageTableString> strings = data.getStrings();
        int columns = data.getColumns() + 1;
        List<PageTableLegend> legend = data.getLegend();

        // First, collapse 'strings' into a header and content row
        if (strings.size() % columns != 0) {
            throw new IllegalArgumentException(
                    String.format("Data should divide columns, got %d len and %d columns",
                            strings.size(), columns));
        }

        int rows = strings.size() / columns;

        if (rows <= 1) {
            throw new IllegalArgumentException(
                    String.format("Should have > 1 rows, got %d", rows));
        }

        List<PageTableString> headers = strings.subList(0, columns);
        List<List<PageTableString>> body = new java.util.ArrayList<>();
        for (int i = 1; i < rows; i++) {
            body.add(strings.subList(i * columns, (i + 1) * columns));
        }

        // Title + text
        formatTitle(entry, buffer, data.getTitle());
        formatText(entry, buffer, data.getText());

        if (legend != null && !legend.isEmpty()) {
            buffer.add("<div class=\"row\"><div class=\"col-md-9\">");
        }

        // Build the HTML table
        buffer.add("<figure class=\"table-figure\"><table><thead><tr>");
        for (PageTableString header : headers) {
            buffer.add(getComponent(header, "th"));
        }
        buffer.add("</tr></thead><tbody>");
        for (List<PageTableString> row : body) {
            buffer.add("<tr>");
            for (PageTableString td : row) {
                buffer.add(getComponent(td, "td"));
            }
            buffer.add("</tr>");
        }
        buffer.add("</tbody></table></figure>");

        if (legend != null && !legend.isEmpty()) {
            buffer.add("</div><div class=\"col-md-3\"><h4>Legend</h4>");
            for (PageTableLegend it : legend) {
                // These are just a color square followed by a name
                String color = it.getColor().substring(2); // Remove the "2:" prefix
                String text = it.getText();
                buffer.add(String.format(
                        """
                        <div class="item-header">
                            <span style="background-color:#%s"></span>
                            <p>%s</p>
                        </div>
                        """, color, text));
            }
            buffer.add("</div></div>");
        }
    }

    private static String getComponent(PageTableString th, String key) {
        if (th.getFill() != null) {
            // Solid fill
            String color = th.getFill().substring(2); // Remove the "2:" prefix
            return String.format("<%s style=\"background-color:#%s;\"></%s>", key, color, key);
        }

        String text = th.getText();
        if (text.isEmpty()) {
            return String.format("<%s></%s>", key, key);
        }

        if (th.isBold()) {
            return String.format("<%s><p style=\"font-weight: bold;\">%s</p></%s>", key, text, key);
        } else {
            return String.format("<%s><p>%s</p></%s>", key, text, key);
        }
    }


}
