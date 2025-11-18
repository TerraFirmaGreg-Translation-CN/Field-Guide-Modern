package io.github.tfgcn.fieldguide;

import io.github.tfgcn.fieldguide.asset.Asset;
import io.github.tfgcn.fieldguide.data.patchouli.page.*;
import io.github.tfgcn.fieldguide.data.tfc.page.*;
import io.github.tfgcn.fieldguide.exception.InternalException;
import io.github.tfgcn.fieldguide.gson.JsonUtils;
import io.github.tfgcn.fieldguide.data.patchouli.BookCategory;
import io.github.tfgcn.fieldguide.data.patchouli.BookEntry;
import io.github.tfgcn.fieldguide.data.patchouli.BookPage;
import io.github.tfgcn.fieldguide.asset.ItemImageResult;
import io.github.tfgcn.fieldguide.render.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

import static io.github.tfgcn.fieldguide.render.ImageTemplates.IMAGE_KNAPPING;
import static io.github.tfgcn.fieldguide.render.ImageTemplates.IMAGE_SINGLE;

@Slf4j
public class BookParser {

    public void processAllLanguages(Context context) {
        // load en_us lang

        for (Language lang : Language.values()) {
            log.info("Language: {}", lang);
            parseBook(context.withLang(lang));
            context.sort();
            try {
                context.getHtmlRenderer().buildBookHtml(context);
            } catch (Exception e) {
                log.error("Failed to build book html", e);
            }
        }

        log.info("Done");
        log.info("  Recipes : {} passed / {} failed / {} skipped", 
                context.getRecipesPassed(), context.getRecipesFailed(), context.getRecipesSkipped());
        log.info("  Items   : {} passed / {} failed", 
                context.getItemsPassed(), context.getItemsFailed());
        log.info("  Blocks  : {} passed / {} failed", 
                context.getBlocksPassed(), context.getBlocksFailed());
        log.info("  Total   : {} blocks / {} items / {} images", 
                context.getLastUid().get("block"), 
                context.getLastUid().get("item"), 
                context.getLastUid().get("image"));

//        log.info("Missing language keys: {}", context.getMissingKeys().size());
//        for (String key : context.getMissingKeys()) {
//            System.out.println(key);
//        }
//        log.info("Missing asset keys: {}", context.getLoader().getMissingAssets().size());
//        for (String key : context.getLoader().getMissingAssets()) {
//            System.out.println(key);
//        }
//        log.info("Missing image keys: {}", context.getMissingImages().size());
//        for (String key : context.getMissingImages()) {
//            System.out.println(key);
//        }
    }

    public void parseBook(Context context) {
        parseCategories(context);
        parseEntries(context);
    }

    private void parseCategories(Context context) {
        // assets/tfc/patchouli_books/field_guide/en_us/categories
        String categoriesPath = Constants.getCategoryDir(context.getLang().getKey());
        List<Asset> assets;
        try {
            assets = context.listAssets(categoriesPath);
        } catch (IOException e) {
            log.error("Failed to list assets", e);
            return;
        }
        for (Asset asset : assets) {
            if (asset.getPath().endsWith(".json")) {
                try {
                    parseCategory(context, categoriesPath, asset);
                } catch (Exception e) {
                    log.error("Failed to parse category file: {}, message: {}", asset, e.getMessage());
                }
            }
        }
    }

    private void parseEntries(Context context) {
        // assets/tfc/patchouli_books/field_guide/en_us/entries
        String entriesPath = Constants.getEntryDir(context.getLang().getKey());
        List<Asset> assets;
        try {
            assets = context.listAssets(entriesPath);
        } catch (IOException e) {
            log.error("Failed to list assets", e);
            return;
        }
        for (Asset asset : assets) {
            if (asset.getPath().endsWith(".json")) {
                try {
                    parseEntry(context, entriesPath, asset);
                } catch (Exception e) {
                    log.error("Failed to parse entry file: {}, message: {}", asset, e.getMessage());
                }
            }
        }
    }

    public void parseCategory(Context context, String categoryDir, Asset asset) {
        // get categoryId
        String relativePath = asset.getPath().substring(categoryDir.length() + 1);
        String categoryId = relativePath.substring(0, relativePath.lastIndexOf('.'));

        if (context.getCategoryMap().containsKey(categoryId)) {
            log.info("Category already exists:{}", relativePath);
            return;
        }

        try {
            BookCategory category = JsonUtils.readFile(asset.getInputStream(), BookCategory.class);
            log.debug("Category: {}, Name: {}", categoryId, category.getName());

            category.setId(categoryId);

            // remove "§."
            category.setName(TextFormatter.stripVanillaFormatting(category.getName()));

            // 格式化描述文本
            List<String> descriptionBuffer = new ArrayList<>();
            TextFormatter.formatText(descriptionBuffer, category.getDescription(), context.getKeybindings());
            category.setDescription(String.join("", descriptionBuffer));

            context.addCategory(category);
            context.getCategoryMap().put(categoryId, category);
            
        } catch (IOException e) {
            log.error("Failed to parse category file: {}", asset, e);
        }
    }
    
    public void parseEntry(Context context, String entryDir, Asset asset) {
        // 提取条目ID
        String relativePath = asset.getPath().substring(entryDir.length() + 1);
        String entryId = relativePath.substring(0, relativePath.lastIndexOf('.'));

        if (context.hasEntry(entryId)) {
            log.debug("Entry {}@{} already exists, skipping", entryId, asset);
            return;
        }

        try {
            BookEntry entry = JsonUtils.readFile(asset.getInputStream(), BookEntry.class);

            // 获取分类ID
            String categoryId = entry.getCategory();
            if (categoryId.contains(":")) {
                categoryId = categoryId.substring(categoryId.indexOf(':') + 1);
            }

            entry.setName(TextFormatter.stripVanillaFormatting(entry.getName()));
            entry.setId(entryId);

            try {
                entry.setRelId(entryId.split("/")[1]);
            } catch (Exception e) {
                entry.setRelId(entryId);
                log.error("Failed to split entryId: {}", entryId);
            }

            try {
                ItemImageResult itemSrc = context.getItemImage(entry.getIcon(), false);
                if (itemSrc != null) {
                    entry.setIconPath(itemSrc.getPath());
                    entry.setIconName(itemSrc.getName());
                } else {
                    log.error("Item image is null for entry: {}", entryId);
                }
            } catch (Exception e) {
                log.error("Failed to get item image for entry: {}", entryId);
            }
            
            Map<String, String> search = new HashMap<>();
            search.put("content", "");
            search.put("entry", entry.getName());
            search.put("url", "./" + categoryId + "/" + entry.getRelId() + ".html");
            
            // 解析页面
            List<BookPage> pages = entry.getPages();
            if (pages != null) {
                for (BookPage page : pages) {
                    if (page instanceof PageTemplate) {
                        log.debug("Page: {}, {}", page.getType(), page.getJsonObject());
                    }
                    try {
                        parsePage(context, entryId, entry.getBuffer(), page, search);
                    } catch (InternalException e) {
                        log.error("Failed to parse page: {}", page, e);
                    }
                }
            }
            // render inner html
            if (!entry.isRendered()) {
                entry.setInnerHtml(String.join("", entry.getBuffer()));
                entry.setRendered(true);
            }

            context.addEntry(categoryId, entryId, entry, search);
            
        } catch (IOException e) {
            log.error("Failed to parse entry file: {}", asset, e);
        }
    }
    
    public void parsePage(Context context, String entryId, List<String> buffer,
                         BookPage page, Map<String, String> search) {
        String pageType = page.getType();
        String anchor = page.getAnchor();
        if (anchor != null) {
            buffer.add(String.format("<a class=\"anchor\" id=\"%s\"></a>", anchor));
        }

        switch (page) {
            case PageText pageText: {// patchouli:text
                context.formatTitle(buffer, pageText.getTitle(), search);
                context.formatText(buffer, pageText.getText(), search);
                break;
            }
            case PageImage pageImage: {// patchouli:image
                context.formatTitle(buffer, pageImage.getTitle(), search);

                List<String> images = pageImage.getImages();
                List<Map.Entry<String, String>> processedImages = new ArrayList<>();

                if (images != null) {
                    for (String image : images) {
                        try {
                            String convertedImage = context.convertImage(image);
                            processedImages.add(Map.entry(image, convertedImage));
                        } catch (InternalException e) {
                            log.error("Failed to convert entry image: {} @ {}", image, entryId, e);
                        }
                    }
                }

                if (processedImages.size() == 1) {
                    Map.Entry<String, String> imageEntry = processedImages.getFirst();
                    buffer.add(String.format(IMAGE_SINGLE,
                            imageEntry.getValue(), imageEntry.getKey()));
                } else if (!processedImages.isEmpty()) {
                    String uid = context.nextId();
                    StringBuilder parts = new StringBuilder();
                    StringBuilder seq = new StringBuilder();

                    for (int i = 0; i < processedImages.size(); i++) {
                        Map.Entry<String, String> imageEntry = processedImages.get(i);
                        String active = i == 0 ? "active" : "";
                        parts.append(String.format(ImageTemplates.IMAGE_MULTIPLE_PART,
                                active, imageEntry.getValue(), imageEntry.getKey()));

                        if (i > 0) {
                            seq.append(String.format(ImageTemplates.IMAGE_MULTIPLE_SEQ,
                                    uid, i, i + 1));
                        }
                    }

                    buffer.add(MessageFormat.format(ImageTemplates.IMAGE_MULTIPLE, uid, seq.toString(), parts.toString()));
                }

                context.formatCenteredText(buffer, pageImage.getText());
                break;
            }
            case PageCrafting pageCrafting: {// patchouli:crafting
                context.formatTitle(buffer, pageCrafting.getTitle(), search);
                parseCraftingRecipe(context, buffer, pageCrafting, search);
                context.formatText(buffer, pageCrafting.getText(), search);
                break;
            }
            case PageSpotlight pageSpotlight: {// patchouli:spotlight
                parseSpotlightPage(context, buffer, pageSpotlight, search);
                break;
            }
            case PageEntity pageEntity: {// patchouli:entity
                context.formatTitle(buffer, pageEntity.getName(), search);
                context.formatText(buffer, pageEntity.getText(), search);
                break;
            }
            case PageEmpty ignored: {// patchouli:empty
                buffer.add("<hr>");
                break;
            }
            case PageMultiblock pageMultiblock: {// patchouli:multiblock
                parseMultiblockPage(context, buffer, pageMultiblock, search);
                break;
            }
            case PageMultiMultiblock pageMultiMultiblock: {// tfc:multimultiblock
                parseMultiMultiblockPage(context, buffer, pageMultiMultiblock, search);
                break;
            }
            case PageHeating PageHeating: {// tfc:heating
                parseMiscRecipe(context, buffer, PageHeating, search, pageType);
                break;
            }
            case PageQuern pageQuern:{// tfc:quern
                parseMiscRecipe(context, buffer, pageQuern, search, pageType);
                break;
            }
            case PageLoom pageLoom: {// tfc:loom
                parseMiscRecipe(context, buffer, pageLoom, search, pageType);
                break;
            }
            case PageAnvil pageAnvil: {// tfc:anvil
                parseMiscRecipe(context, buffer, pageAnvil, search, pageType);
                break;
            }
            case PageGlassworking pageGlassworking: {// tfc:glassworking
                parseMiscRecipe(context, buffer, pageGlassworking, search, pageType);
                break;
            }
            case PageSmelting pageSmelting: {// tfc:smelting
                parseMiscRecipe(context, buffer, pageSmelting, search, pageType);
                break;
            }
            case PageDrying pageDrying: {// tfc:drying
                parseMiscRecipe(context, buffer, pageDrying, search, pageType);
                break;
            }
            case PageBarrel pageBarrel: {// tfc:instant_barrel_recipe, tfc:sealed_barrel_recipe
                parseBarrelRecipe(context, buffer, pageBarrel, search, pageType);
                break;
            }
            case PageWelding pageWelding: {// tfc:welding_recipe
                context.formatRecipe(buffer, pageWelding.getRecipe());
                context.formatText(buffer, pageWelding.getText(), search);
                context.setRecipesSkipped(context.getRecipesSkipped() + 1);
                break;
            }
            case PageRockKnapping pageRockKnapping: {// tfc:rock_knapping_recipe, tfc:clay_knapping_recipe, tfc:fire_clay_knapping_recipe, tfc:leather_knapping_recipe
                parseRockKnappingRecipe(context, buffer, pageRockKnapping, search);
                break;
            }
            case PageKnapping pageKnapping: {// tfc:knapping
                parseKnappingRecipe(context, buffer, pageKnapping, search);
                break;
            }
            case PageTable pageTable: {// tfc:table, tfc:table_small
                parseTablePage(context, buffer, pageTable);
                break;
            }
            default: {
                log.warn("Unrecognized page type: {}, {}", pageType, page);
            }
        }
    }
    
    private void parseCraftingRecipe(Context context, List<String> buffer,
                                     PageCrafting page, Map<String, String> search) {
        // 处理主要配方
        if (page.getRecipe() != null) {
            try {
                CraftingRecipeFormatter.formatCraftingRecipe(context, buffer, page.getRecipe());
                context.setRecipesPassed(context.getRecipesPassed() + 1);
            } catch (Exception e) {
                // TODO add "e" later
                log.error("Recipe processing craft failed: {}. e: {}", page.getRecipe(), e.getMessage());
                context.formatRecipe(buffer, page.getRecipe());
                context.setRecipesFailed(context.getRecipesFailed() + 1);
            }
        }
        
        // 处理第二个配方
        if (page.getRecipe2() != null) {
            try {
                CraftingRecipeFormatter.formatCraftingRecipe(context, buffer, page.getRecipe2());
                context.setRecipesPassed(context.getRecipesPassed() + 1);
            } catch (Exception e) {
                // TODO add e later
                log.error("Recipe2 processing failed: {}, message: {}", page.getRecipe2(), e.getMessage());
                context.formatRecipe(buffer, page.getRecipe2());
                context.setRecipesFailed(context.getRecipesFailed() + 1);
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private void parseSpotlightPage(Context context, List<String> buffer,
                                    PageSpotlight page, Map<String, String> search) {
        List<PageSpotlightItem> items = page.getItem();
        if (items == null || items.isEmpty()) {
            log.warn("Spotlight page did not have an item or tag key: {}", page);
            return;
        }
        try {
            for (PageSpotlightItem item : items) {
                if ("tag".equals(item.getType())) {
                    ItemImageResult itemResult = context.getItemImage("#" + item.getText(), false);
                    context.formatTitleWithIcon(buffer, itemResult.getPath(), itemResult.getName(), page.getTitle());
                } else {
                    ItemImageResult itemResult = context.getItemImage(item.getText(), false);
                    context.formatTitleWithIcon(buffer, itemResult.getPath(), itemResult.getName(), page.getTitle());
                }
                context.setItemsPassed(context.getItemsPassed() + 1);
            }
        } catch (Exception e) {
            // Fallback
            context.formatTitle(buffer, page.getTitle(), search);

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
            String itemHtml = String.format("%s: %s", context.translate(count > 1 ? I18n.ITEMS : I18n.ITEM), sb);
            context.formatWithTooltip(buffer, itemHtml, context.translate(I18n.ITEM_ONLY_IN_GAME));
            context.setItemsFailed(context.getItemsFailed() + 1);
        }

        context.formatText(buffer, page.getText(), search);
    }

    private void parseMultiblockPage(Context context, List<String> buffer,
                                     PageMultiblock page, Map<String, String> search) {
        context.formatTitle(buffer, page.getName(), search);
        
        try {
            String src = context.getMultiBlockImage(page);
            buffer.add(String.format(IMAGE_SINGLE, src, "Block Visualization"));
            context.formatCenteredText(buffer, page.getText());
            context.setBlocksPassed(context.getBlocksPassed() + 1);
        } catch (Exception e) {
            // FIXME add me later log.error("Multiblock image processing failed, message: {}", e.getMessage());
            // Fallback
            if (page.getMultiblockId() != null) {
                context.formatWithTooltip(buffer,
                        String.format("%s: <code>%s</code>", context.translate(I18n.MULTIBLOCK), page.getMultiblockId()),
                        context.translate(I18n.MULTIBLOCK_ONLY_IN_GAME));
            } else {
                // FIXME for debug
                context.formatWithTooltip(buffer,
                        String.format("%s: <code>%s</code>", context.translate(I18n.MULTIBLOCK), JsonUtils.toJson(page.getMultiblock())),
                        context.translate(I18n.MULTIBLOCK_ONLY_IN_GAME));
            }
            context.formatCenteredText(buffer, page.getText());
            context.setBlocksFailed(context.getBlocksFailed() + 1);
        }
    }

    private void parseMultiMultiblockPage(Context context, List<String> buffer,
                                     PageMultiMultiblock page, Map<String, String> search) {
        try {
            String src = context.getMultiBlockImage(page);
            buffer.add(String.format(IMAGE_SINGLE, src, "Block Visualization"));
            context.formatCenteredText(buffer, page.getText());
            context.setBlocksPassed(context.getBlocksPassed() + 1);
        } catch (Exception e) {
            // TODO 日志太多暂时移除 log.error("tfc:multimultiblock image processing failed", e);
            // FIXME for debug
            for (TFCMultiblockData multiblock : page.getMultiblocks()) {
                context.formatWithTooltip(buffer,
                        String.format("%s: <code>%s</code>", context.translate(I18n.MULTIBLOCK), JsonUtils.toJson(multiblock)),
                        context.translate(I18n.MULTIBLOCK_ONLY_IN_GAME));
            }
            // TODO context.formatWithTooltip(buffer, context.translate(I18n.MULTIBLOCK), context.translate(I18n.MULTIBLOCK_ONLY_IN_GAME));
            context.formatCenteredText(buffer, page.getText());
            context.setBlocksFailed(context.getBlocksFailed() + 1);
        }
    }

    private void parseMiscRecipe(Context context, List<String> buffer,
                                 IPageDoubleRecipe page, Map<String, String> search, String pageType) {
        try {
            MiscRecipeFormatter.formatMiscRecipe(context, buffer, page.getRecipe());
            context.setRecipesPassed(context.getRecipesPassed() + 1);
        } catch (Exception e) {
            // TODO add e later
            log.error("Misc recipe processing failed: {}, message: {}", pageType, e.getMessage());
            context.formatRecipe(buffer, page.getRecipe());
            context.setRecipesFailed(context.getRecipesFailed() + 1);
        }

        context.formatText(buffer, page.getText(), search);
    }
    
    private void parseBarrelRecipe(Context context, List<String> buffer, 
                                 PageBarrel page, Map<String, String> search, String pageType) {
        try {
            BarrelRecipeFormatter.formatBarrelRecipe(context, buffer, page.getRecipe());
            context.setRecipesPassed(context.getRecipesPassed() + 1);
        } catch (Exception e) {
            log.error("Barrel recipe processing failed: {}", pageType, e);
            context.formatRecipe(buffer, page.getRecipe());
            context.setRecipesFailed(context.getRecipesFailed() + 1);
        }
    }

    private void parseRockKnappingRecipe(Context context, List<String> buffer,
                                     PageRockKnapping page, Map<String, String> search) {
        try {
            String recipeId = page.getRecipes().getFirst();
            KnappingRecipe recipe = KnappingRecipes.formatKnappingRecipe(context, recipeId);
            buffer.add(String.format(IMAGE_KNAPPING, recipe.image(), "Recipe: " + recipeId));
            context.setRecipesPassed(context.getRecipesPassed() + 1);
        } catch (Exception e) {
            // TODO add e later
            log.error("Failed to load knapping page: {}, message: {}", page.getRecipes(), e.getMessage());
            context.formatRecipe(buffer, page.getRecipes().getFirst());
            context.setRecipesFailed(context.getRecipesFailed() + 1);
        }

        context.formatText(buffer, page.getText(), search);
    }

    private void parseKnappingRecipe(Context context, List<String> buffer,
                                     PageKnapping page, Map<String, String> search) {
        try {
            KnappingRecipe recipe = KnappingRecipes.formatKnappingRecipe(context, page.getRecipe());
            buffer.add(String.format(IMAGE_KNAPPING, recipe.image(), "Recipe: " + recipe.recipeId()));
            context.setRecipesPassed(context.getRecipesPassed() + 1);
        } catch (Exception e) {
            log.error("Failed to load knapping page: {}", page, e);
            context.formatRecipe(buffer, page.getRecipe());
            context.setRecipesFailed(context.getRecipesFailed() + 1);
        }
        
        context.formatText(buffer, page.getText(), search);
    }
    
    private void parseTablePage(Context context, List<String> buffer, PageTable page) {
        try {
            TableFormatter.formatTable(context, buffer, page);
        } catch (InternalException e) {
            log.error("Table formatting failed", e);
        }
    }

}