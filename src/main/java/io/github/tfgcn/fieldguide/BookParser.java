package io.github.tfgcn.fieldguide;

import freemarker.template.TemplateException;
import io.github.tfgcn.fieldguide.asset.Asset;
import io.github.tfgcn.fieldguide.book.BookCategory;
import io.github.tfgcn.fieldguide.book.BookEntry;
import io.github.tfgcn.fieldguide.book.BookPage;
import io.github.tfgcn.fieldguide.item.ItemImageResult;
import io.github.tfgcn.fieldguide.item.SpotlightItem;
import io.github.tfgcn.fieldguide.book.page.*;
import io.github.tfgcn.fieldguide.book.page.tfc.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static io.github.tfgcn.fieldguide.render.HtmlRenderer.*;

@Slf4j
public class BookParser {
    
    public void processAllLanguages(Context context) {
        for (String lang : Versions.LANGUAGES) {
            log.info("Language: {}", lang);
            parseBook(context.withLang(lang));
            context.sort();
            try {
                buildBookHtml(context);
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
    }

    public void parseBook(Context context) {
        parseCategories(context, "tfc");
        parseEntries(context, "tfc");
    }

    private void parseCategories(Context context, String ownerId) {
        // assets/tfc/patchouli_books/field_guide/en_us/categories
        String categoriesPath = context.getSourcePath("categories");
        try {
            List<Asset> assets = context.listAssets(categoriesPath);
            for (Asset asset : assets) {
                if (asset.getPath().endsWith(".json")) {
                    parseCategory(context, categoriesPath, asset, ownerId);
                }
            }
        } catch (IOException e) {
            log.error("Failed to list assets", e);
        }
    }

    private void parseEntries(Context context, String ownerId) {
        // assets/tfc/patchouli_books/field_guide/en_us/entries
        String entriesPath = context.getSourcePath("entries");
        try {
            List<Asset> assets = context.listAssets(entriesPath);
            for (Asset asset : assets) {
                if (asset.getPath().endsWith(".json")) {
                    parseEntry(context, entriesPath, asset, ownerId);
                }
            }
        } catch (IOException e) {
            log.error("Failed to list assets", e);
        }
    }

    public void parseCategory(Context context, String categoryDir, Asset asset, String ownerId) {
        // get categoryId
        String relativePath = asset.getPath().substring(categoryDir.length() + 1);
        String categoryId = relativePath.substring(0, relativePath.lastIndexOf('.'));

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

            context.getCategoryOwners().put(categoryId, ownerId);
            context.getCategories().put(categoryId, category);
            
        } catch (IOException e) {
            log.error("Failed to parse category file: {}", asset, e);
        }
    }
    
    public void parseEntry(Context context, String entryDir, Asset asset, String ownerId) {
        // 提取条目ID
        String relativePath = asset.getPath().substring(entryDir.length() + 1);
        String entryId = relativePath.substring(0, relativePath.lastIndexOf('.'));


        try {
            BookEntry entry = JsonUtils.readFile(asset.getInputStream(), BookEntry.class);

            // 获取分类ID
            String categoryId = entry.getCategory();
            if (categoryId.contains(":")) {
                categoryId = categoryId.substring(categoryId.indexOf(':') + 1);
            }

            // 防止覆盖，移除页面
            if (!context.getCategoryOwners().containsKey(categoryId)) {
                log.error("Category {} not found, skipping", categoryId);
                return;
            }
            if (!context.getCategoryOwners().get(categoryId).equals(ownerId)) {
                log.warn("Skipping entry {} as it is an override from {}", entryId, ownerId);
                return;
            }

            entry.setName(TextFormatter.stripVanillaFormatting(entry.getName()));
            entry.setId(entryId);

            try {
                entry.setRelId(entryId.split("/")[1]);
            } catch (Exception e) {
                entry.setRelId(entryId);
                log.error("Failed to split entryId: {}", entryId);
            }

            // 处理图标
            try {
                 // FIXME 这里需要实现 itemLoader.getItemImage
                ItemImageResult itemSrc = context.getItemImage(entry.getIcon(), false);
                if (itemSrc != null) {
                    entry.setIcon(itemSrc.getPath());
                    entry.setIconName(itemSrc.getName());
                } else {
                    log.error("Item image is null for entry: {}", entryId);
                }
            } catch (Exception e) {
                log.error("Failed to get item image for entry: {}", entryId);
            }
            
            Map<String, Object> search = new HashMap<>();
            search.put("content", "");
            search.put("entry", entry.getName());
            search.put("url", "./" + entryId + ".html");
            
            // 解析页面
            List<BookPage> pages = entry.getPages();
            if (pages != null) {
                for (BookPage page : pages) {
                    if (page instanceof PageTemplate) {
                        log.debug("Page: {}, {}", page.getType(), page.getJsonObject());
                    }
                    try {
                        parsePage(context, entryId, entry.getBuffer(), page, search);
                    } catch (InternalError e) {
                        e.warning();
                    }
                }
            }
            
            context.addEntry(categoryId, entryId, entry, search);
            
        } catch (IOException e) {
            log.error("Failed to parse entry file: {}", asset, e);
        }
    }
    
    public void parsePage(Context context, String entryId, List<String> buffer,
                         BookPage page, Map<String, Object> search) {
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
                            // FIXME processedImages.add(Map.entry(image, convertedImage));
                        } catch (InternalError e) {
                            e.prefix("Entry: '" + entryId + "'").warning();
                        }
                    }
                }

                if (processedImages.size() == 1) {
                    Map.Entry<String, String> imageEntry = processedImages.get(0);
                    buffer.add(String.format(ImageTemplates.IMAGE_SINGLE,
                            imageEntry.getValue(), imageEntry.getKey()));
                } else if (!processedImages.isEmpty()) {
                    String uid = context.nextId();
                    StringBuilder parts = new StringBuilder();
                    StringBuilder seq = new StringBuilder();

                    for (int i = 0; i < processedImages.size(); i++) {
                        Map.Entry<String, String> imageEntry = processedImages.get(i);
                        String active = i == 0 ? "active" : "";
                        parts.append(String.format(ImageTemplates.IMAGE_MULTIPLE_PART,
                                imageEntry.getValue(), imageEntry.getKey(), active));

                        if (i > 0) {
                            seq.append(String.format(ImageTemplates.IMAGE_MULTIPLE_SEQ,
                                    uid, i, i + 1));
                        }
                    }

                    buffer.add(String.format(ImageTemplates.IMAGE_MULTIPLE,
                            uid, seq.toString(), parts.toString()));
                }

                // FIXME context.formatCenteredText(buffer, data);
                break;
            }
            case PageCrafting pageCrafting: {// patchouli:crafting
                // FIXME context.formatTitle(buffer, data, "title", search);
                // FIXME parseCraftingRecipe(context, buffer, data, search);
                // FIXME context.formatText(buffer, data, "text", search);
                break;
            }
            case PageSpotlight pageSpotlight: {// patchouli:spotlight
                parseSpotlightPage(context, buffer, pageSpotlight, search);
                break;
            }
            case PageEntity pageEntity: {// patchouli:entity
                // FIXME context.formatTitle(buffer, data, "name", search);
                // FIXME context.formatText(buffer, data, "text", search);
                break;
            }
            case PageEmpty pageEmpty: {// patchouli:empty
                buffer.add("<hr>");
                break;
            }
            case PageMultiblock pageMultiblock: {// patchouli:multiblock, tfc:multimultiblock
                // FIXME parseMultiblockPage(context, buffer, data, search);
                break;
            }
            case PageHeating PageQuern: {// tfc:quern
                // FIXME parseMiscRecipe(context, buffer, data, search, pageType);
                break;
            }
            case PageQuern pageQuern:{// tfc:loom
                // FIXME parseMiscRecipe(context, buffer, data, search, pageType);
                break;
            }
            case PageLoom pageLoom: {// tfc:loom
                // FIXME parseMiscRecipe(context, buffer, data, search, pageType);
                break;
            }
            case PageAnvil pageAnvil: {// tfc:anvil
                // FIXME parseMiscRecipe(context, buffer, data, search, pageType);
                break;
            }
            case PageGlassworking pageGlassworking: {// tfc:glassworking
                // FIXME parseMiscRecipe(context, buffer, data, search, pageType);
                break;
            }
            case PageSmelting pageSmelting: {// tfc:smelting
                // FIXME parseSmeltingPage(context, buffer, data, search);
                break;
            }
            case PageDrying pageDrying: {// tfc:drying
                // FIXME parseDryingPage(context, buffer, data, search);
                break;
            }
            case PageBarrel pageBarrel: {// tfc:instant_barrel_recipe, tfc:sealed_barrel_recipe
                // FIXME parseBarrelRecipe(context, buffer, data, search, pageType);
                break;
            }
            case PageWelding pageWelding: {// tfc:welding_recipe
                // FIXME context.formatRecipe(buffer, data);
                // FIXME context.formatText(buffer, data, "text", search);
                context.setRecipesSkipped(context.getRecipesSkipped() + 1);
                break;
            }
            case PageRockKnapping pageRockKnapping: {// tfc:rock_knapping_recipe, tfc:clay_knapping_recipe, tfc:fire_clay_knapping_recipe, tfc:leather_knapping_recipe
                // FIXME parseKnappingRecipe(context, buffer, page, search);
                break;
            }
            case PageKnapping pageKnapping: {// tfc:knapping
                parseKnappingRecipe(context, buffer, page, search);
                break;
            }
            case PageTable pageTable: {// tfc:table, tfc:table_small
                parseTablePage(context, buffer, page);
                break;
            }
            default: {
                log.warn("Unrecognized page type: {}, {}", pageType, page);
            }
        }
    }
    
    private void parseCraftingRecipe(Context context, List<String> buffer, 
                                   Map<String, Object> data, Map<String, Object> search) {
        // 处理主要配方
        if (data.containsKey("recipe")) {
            try {
                // crafting_recipe.format_crafting_recipe(context, buffer, data['recipe'])
                log.debug("Crafting recipe processing not implemented");
                context.setRecipesPassed(context.getRecipesPassed() + 1);
            } catch (InternalError e) {
                e.prefix("Recipe: '" + data.get("recipe") + "'").warning(true);
                context.formatRecipe(buffer, data);
                context.setRecipesFailed(context.getRecipesFailed() + 1);
            }
        }
        
        // 处理第二个配方
        if (data.containsKey("recipe2")) {
            try {
                // crafting_recipe.format_crafting_recipe(context, buffer, data['recipe2'])
                log.debug("Crafting recipe2 processing not implemented");
                context.setRecipesPassed(context.getRecipesPassed() + 1);
            } catch (InternalError e) {
                e.prefix("Recipe: '" + data.get("recipe2") + "'").warning(true);
                context.formatRecipe(buffer, data, "recipe2");
                context.setRecipesFailed(context.getRecipesFailed() + 1);
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private void parseSpotlightPage(Context context, List<String> buffer,
                                    PageSpotlight data, Map<String, Object> search) {
        try {
            List<SpotlightItem> items = data.getItem();
            if (items == null || items.isEmpty()) {
                log.warn("Spotlight page did not have an item or tag key: {}", data);
                throw new IllegalArgumentException("Spotlight page did not have an item or tag key: " + data);
            }
            for (SpotlightItem item : items) {
                if ("tag".equals(item.getType())) {
                    // item_src, item_name = item_loader.get_item_image(context, '#' + data['item']['tag'], false)
                    // context.format_title_with_icon(buffer, item_src, item_name, data)
                    // FIXME log.debug("Spotlight tag processing not implemented");
                } else {
                    // item_src, item_name = item_loader.get_item_image(context, data['item'], false)
                    // context.format_title_with_icon(buffer, item_src, item_name, data)
                    // FIXME log.debug("Spotlight item processing not implemented");
                }
                context.setItemsPassed(context.getItemsPassed() + 1);
            }
        } catch (Exception e) {
            // Fallback
            // FIXME context.formatTitle(buffer, data);
            // String itemStr = item_loader.decode_item(data['item'])
            // 这里需要实现 item_loader.decode_item
            log.debug("Item decoding not implemented for fallback");
            context.setItemsFailed(context.getItemsFailed() + 1);
        }
        
        // FIXME context.formatText(buffer, data, "text", search);
    }
    
    private void parseMultiblockPage(Context context, List<String> buffer, 
                                   Map<String, Object> data, Map<String, Object> search) {
        context.formatTitle(buffer, data, "name", search);
        
        try {
            // src = block_loader.get_multi_block_image(context, data)
            // buffer.append(IMAGE_SINGLE.format(src=src, text='Block Visualization'))
            log.debug("Multiblock image processing not implemented");
            context.formatCenteredText(buffer, data);
            context.setBlocksPassed(context.getBlocksPassed() + 1);
        } catch (InternalError e) {
            e.warning();
            
            // Fallback
            if (data.containsKey("multiblock_id")) {
                context.formatWithTooltip(buffer, 
                    String.format("%s: <code>%s</code>", 
                        context.translate(I18n.MULTIBLOCK), 
                        data.get("multiblock_id")),
                    context.translate(I18n.MULTIBLOCK_ONLY_IN_GAME));
            } else {
                context.formatWithTooltip(buffer, 
                    context.translate(I18n.MULTIBLOCK),
                    context.translate(I18n.MULTIBLOCK_ONLY_IN_GAME));
            }
            context.formatText(buffer, data);
            context.setBlocksFailed(context.getBlocksFailed() + 1);
        }
    }
    
    private void parseMiscRecipe(Context context, List<String> buffer, 
                               Map<String, Object> data, Map<String, Object> search, String pageType) {
        try {
            // misc_recipe.format_misc_recipe(context, buffer, data['recipe'])
            log.debug("Misc recipe processing not implemented for: {}", pageType);
            context.setRecipesPassed(context.getRecipesPassed() + 1);
        } catch (InternalError e) {
            e.prefix("misc_recipe '" + pageType + "'").warning(true);
            context.formatRecipe(buffer, data);
            context.setRecipesFailed(context.getRecipesFailed() + 1);
        }
        
        context.formatText(buffer, data, "text", search);
    }
    
    private void parseBarrelRecipe(Context context, List<String> buffer, 
                                 Map<String, Object> data, Map<String, Object> search, String pageType) {
        try {
            // barrel_recipe.format_barrel_recipe(context, buffer, data['recipe'])
            log.debug("Barrel recipe processing not implemented for: {}", pageType);
            context.setRecipesPassed(context.getRecipesPassed() + 1);
        } catch (InternalError e) {
            e.prefix("barrel recipe '" + pageType + "'").warning(true);
            context.formatRecipe(buffer, data);
            context.setRecipesFailed(context.getRecipesFailed() + 1);
        }
    }
    
    private void parseKnappingRecipe(Context context, List<String> buffer,
                                     BookPage page, Map<String, Object> search) {
        try {
            // recipe_id, image = knapping_recipe.format_knapping_recipe(context, data)
            // buffer.append(IMAGE_KNAPPING.format(src=image, text='Recipe: ' + recipe_id))
            // TODO log.debug("Knapping recipe processing not implemented");
            context.setRecipesPassed(context.getRecipesPassed() + 1);
        } catch (InternalError e) {
            e.warning(true);
            // FIXME context.formatRecipe(buffer, data);
            context.setRecipesFailed(context.getRecipesFailed() + 1);
        }
        
        // context.formatText(buffer, data, "text", search);
    }
    
    private void parseTablePage(Context context, List<String> buffer, BookPage page) {
        try {
            // table_formatter.format_table(context, buffer, data)
            // FIXME log.debug("Table formatting not implemented");
        } catch (InternalError e) {
            e.warning(true);
        }
    }

    public void buildBookHtml(Context context) throws Exception {

        // copy files from assets/static to outputDir
        copyStaticFiles(context);

        // Home page
        buildHomePage(context);

        // Search page
        // TODO

        // Category pages
        for (Map.Entry<String, BookCategory> entry : context.getSortedCategories()) {
            String categoryId = entry.getKey();
            BookCategory category = entry.getValue();

            // Category Page
            buildCategoryPage(context, categoryId, category);
        }

        System.out.println("Static site generated successfully!");
    }

    public void copyStaticFiles(Context context) throws IOException {

        // copy files from assets/static to outputDir
        FileUtils.copyDirectory(new File("assets/static"), new File(context.getOutputRootDir() + "/static"));
        // copy files from assets/textures to outputDir/_images
        FileUtils.copyDirectory(new File("assets/textures"), new File(context.getOutputRootDir() + "/_images"));
        // Always copy the redirect, which defaults to en_us/
        FileUtils.copyFile(new File("assets/templates/redirect.html"), new File(context.getOutputDir() + "/index.html"));
        // Write metadata.js
        String metadata = "window._VERSIONS = [\n" +
                "    [\"%s - %s\", null, false],\n".formatted(Versions.MC_VERSION, Versions.VERSION) +
                "];";
        FileUtils.writeStringToFile(new File(context.getOutputRootDir() + "/static/metadata.js"), metadata, "UTF-8");
    }

    public void buildHomePage(Context context) throws IOException, TemplateException {
        Map<String, Object> data = new HashMap<>();
        // meta
        data.put("title", context.translate(I18n.TITLE));
        data.put("long_title", context.translate(I18n.TITLE) + " | " + Versions.MC_VERSION);
        data.put("short_description", context.translate(I18n.HOME));
        data.put("preview_image", "splash.png");
        data.put("root", context.getRootDir());
        data.put("tfc_version", Versions.TFC_VERSION);

        // text
        data.put("text_index", context.translate(I18n.INDEX));
        data.put("text_contents", context.translate(I18n.CONTENTS));
        data.put("text_version", context.translate(I18n.VERSION));
        data.put("text_api_docs", context.translate(I18n.API_DOCS));
        data.put("text_github", context.translate(I18n.GITHUB));
        data.put("text_discord", context.translate(I18n.DISCORD));

        // langs and navigation
        data.put("current_lang_key", context.getLang());
        data.put("current_lang", context.translate(String.format(I18n.LANGUAGE_NAME, context.getLang())));
        data.put("langs", generateLanguageDropdown(Versions.LANGUAGES, context));
        data.put("index", "#");
        data.put("location", indexBreadcrumbModern(null));

        // contents
        data.put("contents", generateTableOfContents(context.getSortedCategories()));
        data.put("page_content", generateHomePageContent(context, context.getSortedCategories()));

        // generate page
        context.getHtmlRenderer().generatePage("index.ftl", context.getOutputDir(), "index.html", data);
    }

    public void buildCategoryPage(Context context, String categoryId, BookCategory cat) throws IOException, TemplateException {
        Map<String, Object> data = new HashMap<>();
        data.put("title", context.translate(I18n.TITLE));
        data.put("long_title", cat.getName() + " | " + context.translate(I18n.SHORT_TITLE));
        data.put("short_description", cat.getName());
        data.put("preview_image", "splash.png");
        data.put("text_index", context.translate(I18n.INDEX));
        data.put("text_contents", context.translate(I18n.CONTENTS));
        data.put("text_version", context.translate(I18n.VERSION));
        data.put("text_api_docs", context.translate(I18n.API_DOCS));
        data.put("text_github", context.translate(I18n.GITHUB));
        data.put("text_discord", context.translate(I18n.DISCORD));
        data.put("current_lang_key", context.getLang());
        data.put("current_lang", context.translate(String.format(I18n.LANGUAGE_NAME, context.getLang())));
        data.put("langs", generateCategoryLanguageLinks(Versions.LANGUAGES, context, categoryId));
        data.put("index", "../");
        data.put("root", context.getRootDir());
        data.put("tfc_version", Versions.TFC_VERSION);
        data.put("location", generateCategoryBreadcrumb("../", cat.getName()));
        data.put("contents", generateCategoryTableOfContents(context.getSortedCategories(), categoryId));
        data.put("page_content", generateCategoryPageContent(cat));

        // 生成分类页面
        String outputDir = Paths.get(context.getOutputDir(), categoryId).toString();
        context.getHtmlRenderer().generatePage("index.ftl", outputDir, "index.html", data);

        // 生成该分类下的条目页面
        buildEntryPages(context, categoryId, cat);
    }

    private static String generateCategoryLanguageLinks(List<String> languages, Context context, String categoryId) {
        return languages.stream()
                .map(lang -> String.format(
                        """
                        <a href="../../%s/%s/" class="dropdown-item">%s</a>
                        """,
                        lang, categoryId, context.translate(String.format(I18n.LANGUAGE_NAME, lang))
                ))
                .collect(Collectors.joining("\n"));
    }

    private static String generateCategoryBreadcrumb(String relativePath, String categoryName) {
        String indexBreadcrumb = indexBreadcrumbModern(relativePath);
        return String.format(
                """
                %s
                <li class="breadcrumb-item active" aria-current="page">%s</li>
                """,
                indexBreadcrumb, categoryName
        );
    }

    private static String generateCategoryTableOfContents(List<Map.Entry<String, BookCategory>> sortedCategories, String currentCategoryId) {
        return sortedCategories.stream()
                .map(entry -> {
                    String catId = entry.getKey();
                    BookCategory category = entry.getValue();

                    if (!catId.equals(currentCategoryId)) {
                        return String.format(
                                """
                                <li><a href="../%s/">%s</a></li>
                                """,
                                catId, category.getName()
                        );
                    } else {
                        // 当前分类，显示子条目
                        String subEntries = category.getSortedEntries().stream()
                                .map(subEntry -> {
                                    String entryId = subEntry.getKey();
                                    BookEntry bookEntry = subEntry.getValue();
                                    // 计算相对路径
                                    String relativePath = getRelativePath(entryId, catId);
                                    return String.format(
                                            """
                                            <li><a href="./%s.html">%s</a></li>
                                            """,
                                            relativePath, bookEntry.getName()
                                    );
                                })
                                .collect(Collectors.joining("\n"));

                        return String.format(
                                """
                                <li><a href="../%s/">%s</a>
                                    <ul>
                                    %s
                                    </ul>
                                </li>
                                """,
                                catId, category.getName(), subEntries
                        );
                    }
                })
                .collect(Collectors.joining("\n"));
    }

    private static String generateCategoryPageContent(BookCategory cat) {
        String categoryListing = cat.getSortedEntries().stream()
                .map(entry -> {
                    BookEntry bookEntry = entry.getValue();
                    return String.format(
                            """
                            <div class="col">
                                <div class="card">%s</div>
                            </div>
                            """,
                            entryCardWithDefaultIcon(entry.getKey(), bookEntry.getName(),
                                    bookEntry.getIcon(), bookEntry.getIconName())
                    );
                })
                .collect(Collectors.joining("\n"));

        return String.format(
                """
                <h1 class="mb-4">%s</h1>
                <p>%s</p>
                <div class="row row-cols-1 row-cols-md-3 g-3">
                    %s
                </div>
                """,
                cat.getName(), cat.getDescription(), categoryListing
        );
    }

    private static String getRelativePath(String entryId, String categoryId) {
        // 简化实现，假设 entryId 已经是相对于 categoryId 的路径
        // 如果需要更复杂的路径计算，可以在这里实现
        return entryId.startsWith(categoryId + "/")
                ? entryId.substring(categoryId.length() + 1)
                : entryId;
    }

    private void buildEntryPages(Context context, String categoryId, BookCategory cat) throws IOException, TemplateException {
        for (Map.Entry<String, BookEntry> entryEntry : cat.getSortedEntries()) {
            String entryId = entryEntry.getKey();
            BookEntry entry = entryEntry.getValue();

            Map<String, Object> data = new HashMap<>();
            data.put("title", context.translate(I18n.TITLE));
            data.put("long_title", entry.getName() + " | " + context.translate(I18n.SHORT_TITLE));
            data.put("short_description", entry.getName());
            data.put("preview_image", cleanImagePath(entry.getIcon()));
            data.put("text_index", context.translate(I18n.INDEX));
            data.put("text_contents", context.translate(I18n.CONTENTS));
            data.put("text_version", context.translate(I18n.VERSION));
            data.put("text_api_docs", context.translate(I18n.API_DOCS));
            data.put("text_github", context.translate(I18n.GITHUB));
            data.put("text_discord", context.translate(I18n.DISCORD));
            data.put("current_lang_key", context.getLang());
            data.put("current_lang", context.translate(String.format(I18n.LANGUAGE_NAME, context.getLang())));
            data.put("langs", generateEntryLanguageLinks(Versions.LANGUAGES, context, entryId));
            data.put("index", "../");
            data.put("root", context.getRootDir());
            data.put("tfc_version", Versions.TFC_VERSION);
            data.put("location", generateEntryBreadcrumb("../", cat.getName(), entry.getName()));
            data.put("contents", generateEntryTableOfContents(context.getSortedCategories(), categoryId, entryId));
            data.put("page_content", generateEntryPageContent(entry));

            // 生成条目页面
            String outputFileName = entryId + ".html";
            context.getHtmlRenderer().generatePage("index.ftl", context.getOutputDir(), outputFileName, data);
        }
    }

    private static String cleanImagePath(String iconPath) {
        if (iconPath == null) return "";
        return iconPath.replace("../../_images/", "").replace("..\\..\\_images\\", "");
    }

    private static String generateEntryLanguageLinks(List<String> languages, Context context, String entryId) {
        return languages.stream()
                .map(lang -> String.format(
                        """
                        <a href="../../%s/%s.html" class="dropdown-item">%s</a>
                        """,
                        lang, entryId, context.translate(String.format(I18n.LANGUAGE_NAME, lang))
                ))
                .collect(Collectors.joining("\n"));
    }

    private static String generateEntryBreadcrumb(String relativePath, String categoryName, String entryName) {
        String indexBreadcrumb = indexBreadcrumbModern(relativePath);
        return String.format(
                """
                %s
                <li class="breadcrumb-item"><a href="./">%s</a></li>
                <li class="breadcrumb-item active" aria-current="page">%s</li>
                """,
                indexBreadcrumb, categoryName, entryName
        );
    }

    private static String generateEntryTableOfContents(List<Map.Entry<String, BookCategory>> sortedCategories,
                                                       String currentCategoryId, String currentEntryId) {
        return sortedCategories.stream()
                .map(entry -> {
                    String catId = entry.getKey();
                    BookCategory category = entry.getValue();

                    if (!catId.equals(currentCategoryId)) {
                        return String.format(
                                """
                                <li><a href="../%s/">%s</a></li>
                                """,
                                catId, category.getName()
                        );
                    } else {
                        // 当前分类，显示子条目
                        String subEntries = category.getSortedEntries().stream()
                                .map(subEntry -> {
                                    String entryId = subEntry.getKey();
                                    BookEntry bookEntry = subEntry.getValue();
                                    String relativePath = getRelativePath(entryId, catId);
                                    boolean isCurrent = entryId.equals(currentEntryId);

                                    return String.format(
                                            """
                                            <li><a href="./%s.html"%s>%s</a></li>
                                            """,
                                            relativePath,
                                            isCurrent ? " class=\"active\"" : "",
                                            bookEntry.getName()
                                    );
                                })
                                .collect(Collectors.joining("\n"));

                        return String.format(
                                """
                                <li><a href="../%s/">%s</a>
                                    <ul>
                                    %s
                                    </ul>
                                </li>
                                """,
                                catId, category.getName(), subEntries
                        );
                    }
                })
                .collect(Collectors.joining("\n"));
    }

    private static String generateEntryPageContent(BookEntry entry) {
        String titleWithIcon = titleWithOptionalIcon(entry.getName(), entry.getIcon(), entry.getIconName());
        String innerContent = String.join("", entry.getBuffer());

        return String.format(
                """
                <h1 class="d-flex align-items-center mb-4">%s</h1>
                %s
                """,
                titleWithIcon, innerContent
        );
    }

    private static String entryCardWithDefaultIcon(String relId, String name, String icon, String iconName) {
        // FIXME 实现 entry_card_with_default_icon 逻辑
        // 这里需要根据你的具体需求实现
        return String.format(
                """
                <div class="card-header">
                    <a href="%s.html">%s</a>
                </div>
                <div class="card-body">
                    <img src="%s" alt="%s" class="img-fluid">
                </div>
                """,
                relId, name, icon, iconName != null ? iconName : name
        );
    }

    private static String titleWithOptionalIcon(String name, String icon, String iconName) {
        if (icon != null && !icon.isEmpty()) {
            return String.format(
                    """
                    <img src="%s" alt="%s" class="me-2" style="height: 1.5em;">%s
                    """,
                    icon, iconName != null ? iconName : name, name
            );
        } else {
            return name;
        }
    }
}