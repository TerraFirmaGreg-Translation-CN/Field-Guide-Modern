package team.terrafirmgreg.fieldguide.render;

import team.terrafirmgreg.fieldguide.export.ExportModelLoader;
import team.terrafirmgreg.fieldguide.asset.ItemImageResult;
import team.terrafirmgreg.fieldguide.data.patchouli.BookEntry;
import team.terrafirmgreg.fieldguide.data.patchouli.BookPage;
import team.terrafirmgreg.fieldguide.data.patchouli.page.*;
import team.terrafirmgreg.fieldguide.data.tfc.page.*;
import team.terrafirmgreg.fieldguide.exception.InternalException;
import team.terrafirmgreg.fieldguide.gson.JsonUtils;
import team.terrafirmgreg.fieldguide.localization.I18n;
import team.terrafirmgreg.fieldguide.localization.LocalizationManager;
import team.terrafirmgreg.fieldguide.site.emi.EmiRecipeIndex;
import lombok.extern.slf4j.Slf4j;

import java.text.MessageFormat;
import java.util.*;
import java.util.List;

import static team.terrafirmgreg.fieldguide.render.ImageTemplates.IMAGE_SINGLE;

@Slf4j
public class PageRenderer {


    private final ExportModelLoader assetLoader;
    private final TextureRenderer textureRenderer;
    private final LocalizationManager localizationManager;
    private final EmiRecipeIndex emiRecipes;
    /** Handbook recipe id → EMI recipe id for {@code data-recipe-id}. */
    private final Map<String, String> recipeMountIds;

    private int id = 0;

    public PageRenderer(
            ExportModelLoader loader,
            LocalizationManager localizationManager,
            TextureRenderer textureRenderer,
            EmiRecipeIndex emiRecipes,
            Map<String, String> recipeMountIds) {
        this.assetLoader = loader;
        this.localizationManager = localizationManager;
        this.textureRenderer = textureRenderer;
        this.emiRecipes = emiRecipes;
        this.recipeMountIds = recipeMountIds == null ? Map.of() : Map.copyOf(recipeMountIds);
    }

    public void renderPage(BookEntry entry, BookPage page) {
        String pageType = page.getType();
        String anchor = page.getAnchor();
        if (anchor != null) {
            entry.getBuffer().add(String.format("<a class=\"anchor\" id=\"%s\"></a>", anchor));
        }

        List<String> buffer = entry.getBuffer();
        if (page instanceof PageText pageText) {
            formatTitle(entry, buffer, pageText.getTitle());
            formatText(entry, buffer, pageText.getText());
        } else if (page instanceof PageImage pageImage) {
            formatTitle(entry, buffer, pageImage.getTitle());
            renderImagePage(buffer, pageImage.getImages());
            formatCenteredText(entry, buffer, pageImage.getText());
        } else if (page instanceof PageCrafting pageCrafting) {
            formatTitle(entry, buffer, pageCrafting.getTitle());
            formatDoubleRecipePage(buffer, pageCrafting);
            formatText(entry, buffer, pageCrafting.getText());
        } else if (page instanceof PageSpotlight pageSpotlight) {
            parseSpotlightPage(entry, buffer, pageSpotlight);
            formatText(entry, buffer, pageSpotlight.getText());
        } else if (page instanceof PageEntity pageEntity) {
            formatTitle(entry, buffer, pageEntity.getName());
            // TODO support entity
            formatText(entry, buffer, pageEntity.getText());
        } else if (page instanceof PageEmpty) {
            buffer.add("<hr>");
        } else if (page instanceof PageMultiblock pageMultiblock) {
            formatTitle(entry, buffer, pageMultiblock.getName());
            parseMultiblockPage(buffer, pageMultiblock);
            formatCenteredText(entry, buffer, pageMultiblock.getText());
        } else if (page instanceof PageMultiMultiblock pageMultiMultiblock) {
            parseMultiMultiblockPage(buffer, pageMultiMultiblock);
            formatCenteredText(entry, buffer, pageMultiMultiblock.getText());
        } else if (page instanceof PageHeating pageHeating) {
            formatDoubleRecipePage(buffer, pageHeating);
            formatText(entry, buffer, pageHeating.getText());
        } else if (page instanceof PageQuern pageQuern) {
            formatDoubleRecipePage(buffer, pageQuern);
            formatText(entry, buffer, pageQuern.getText());
        } else if (page instanceof PageLoom pageLoom) {
            formatDoubleRecipePage(buffer, pageLoom);
            formatText(entry, buffer, pageLoom.getText());
        } else if (page instanceof PageAnvil pageAnvil) {
            formatDoubleRecipePage(buffer, pageAnvil);
            formatText(entry, buffer, pageAnvil.getText());
        } else if (page instanceof PageBetterAnvil pageBetterAnvil) {
            parseBetterAnvilPage(buffer, pageBetterAnvil);
            if (pageBetterAnvil.getText4() != null && !pageBetterAnvil.getText4().isBlank()) {
                formatText(entry, buffer, pageBetterAnvil.getText4());
            }
        } else if (page instanceof PageGlassworking pageGlassworking) {
            formatDoubleRecipePage(buffer, pageGlassworking);
            formatText(entry, buffer, pageGlassworking.getText());
        } else if (page instanceof PageSmelting pageSmelting) {
            formatDoubleRecipePage(buffer, pageSmelting);
            formatText(entry, buffer, pageSmelting.getText());
        } else if (page instanceof PageDrying pageDrying) {
            formatDoubleRecipePage(buffer, pageDrying);
            formatText(entry, buffer, pageDrying.getText());
        } else if (page instanceof PageBarrel pageBarrel) {
            formatDoubleRecipePage(buffer, pageBarrel);
            formatText(entry, buffer, pageBarrel.getText());
        } else if (page instanceof PageWelding pageWelding) {
            formatDoubleRecipePage(buffer, pageWelding);
            formatText(entry, buffer, pageWelding.getText());
        } else if (page instanceof PageRockKnapping pageRockKnapping) {
            formatRecipeList(buffer, pageRockKnapping.getRecipes());
            formatText(entry, buffer, pageRockKnapping.getText());
        } else if (page instanceof PageKnapping pageKnapping) {
            formatDoubleRecipePage(buffer, pageKnapping);
            formatText(entry, buffer, pageKnapping.getText());
        } else if (page instanceof PageTable pageTable) {
            parseTablePage(entry, buffer, pageTable);
            formatText(entry, buffer, pageTable.getText());
        } else {
            log.warn("Unrecognized page type: {}, {}", pageType, page);
        }
    }


    public void formatText(BookEntry entry, List<String> buffer, String text) {
        if (text != null && !text.isEmpty()) {
            TextFormatter.formatText(buffer, text, localizationManager);

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
    public void formatTitleWithIcon(BookEntry entry, List<String> buffer, ItemImageResult icon, String title) {
        formatTitleWithIcon(entry, buffer, icon, title, "h5", null);
    }

    public void formatTitleWithIcon(BookEntry entry, List<String> buffer, ItemImageResult icon,
                                    String inTitle, String tag,
                                    String tooltip) {
        String title = icon.getName();
        if (inTitle != null && !inTitle.isEmpty()) {
            title = TextFormatter.stripVanillaFormatting(inTitle);
        }
        if (tooltip == null) {
            tooltip = title != null ? title : "";
        }
        if (title != null && !title.isEmpty()) {
            entry.addSearchContent(title);
        }

        String iconHtml = IconMarkup.img(icon, "icon-title");
        String html = String.format("""
            <div class="item-header">
                <span href="#" data-bs-toggle="tooltip" title="%s">
                    %s
                </span>
                <%s>%s</%s>
            </div>
            """, tooltip, iconHtml, tag, title, tag);

        buffer.add(html);
    }

    public void formatTitleWithIcon(BookEntry entry, List<String> buffer, String iconSrc, String iconName,
                                    String inTitle, String tag,
                                    String tooltip) {
        formatTitleWithIcon(
                entry,
                buffer,
                ItemImageResult.legacy(iconSrc, iconName, null),
                inTitle,
                tag,
                tooltip);
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
        if (recipeId == null || recipeId.isEmpty()) {
            return;
        }
        String mountId = recipeMountIds.getOrDefault(recipeId, recipeId);
        if (emiRecipes != null && !emiRecipes.isEmpty() && !emiRecipes.contains(mountId)) {
            String tmrvFallback = tmrvRecipeId(recipeId);
            if (tmrvFallback != null && emiRecipes.contains(tmrvFallback)) {
                mountId = tmrvFallback;
            }
        }
        if (emiRecipes != null && !emiRecipes.isEmpty() && !emiRecipes.contains(mountId)) {
            log.debug("Recipe not in EMI export, using in-game-only fallback: {} (mount={})", recipeId, mountId);
            String text = String.format(
                    "%s: <code>%s</code>",
                    localizationManager.translate(I18n.RECIPE),
                    escapeHtmlText(recipeId));
            formatWithTooltip(buffer, text, localizationManager.translate(I18n.RECIPE_ONLY_IN_GAME));
            return;
        }
        buffer.add(String.format(
                "<div class=\"emi-recipe my-2\" data-recipe-id=\"%s\"></div>",
                escapeHtmlAttr(mountId)));
    }

    /** Same id shape as EMI export when Patchouli uses {@code mod:path/with/slashes}. */
    private static String tmrvRecipeId(String handbookRecipeId) {
        if (handbookRecipeId == null || handbookRecipeId.isBlank() || handbookRecipeId.indexOf(':') <= 0) {
            return null;
        }
        return "toomanyrecipeviewers:/" + handbookRecipeId.replace(':', '/');
    }

    private static String escapeHtmlText(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeHtmlAttr(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;");
    }

    private void formatDoubleRecipePage(List<String> buffer, IPageDoubleRecipe page) {
        formatRecipe(buffer, page.getRecipe());
        if (page.getRecipe2() != null && !page.getRecipe2().isBlank()) {
            formatRecipe(buffer, page.getRecipe2());
        }
    }

    private void formatRecipeList(List<String> buffer, List<String> recipeIds) {
        if (recipeIds == null) {
            return;
        }
        for (String id : recipeIds) {
            formatRecipe(buffer, id);
        }
    }

    private void parseBetterAnvilPage(List<String> buffer, PageBetterAnvil page) {
        for (String id : List.of(page.getRecipe(), page.getRecipe2(), page.getRecipe3(), page.getRecipe4())) {
            if (id == null || id.isBlank()) {
                continue;
            }
            formatRecipe(buffer, id);
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
            Map.Entry<String, String> imageEntry = processedImages.get(0);
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
                    String tagId = item.getText();
                    ItemImageResult itemResult = ItemImageResult.emiTag(
                            tagId,
                            localizationManager.translateWithArgs(I18n.TAG, "#" + tagId));
                    formatTitleWithIcon(entry, buffer, itemResult, page.getTitle());
                } else {
                    ItemImageResult itemResult = textureRenderer.getItemImage(item.getText(), false);
                    formatTitleWithIcon(entry, buffer, itemResult, page.getTitle());
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
            
            // 只添加GLB 3D模型查看器，不要2D图片
            if (src != null && src.endsWith(".png")) {
                String glbPath = src.substring(0, src.length() - 4) + ".glb";
                String viewerId = generateUniqueViewerId("multiblock");
                
                // 添加GLB查看器div（手动加载模式）
                buffer.add(String.format("""
                    <div class="glb-viewer-container">
                        <div id="%s" 
                             class="glb-viewer" 
                             data-glb-viewer="../../%s"
                             data-viewer-type="multiblock"
                             data-auto-rotate="true"
                             data-auto-load="false">
                            <div class="glb-viewer-loading">
                                <div class="spinner-border" role="status">
                                    <span class="visually-hidden">Loading 3D model...</span>
                                </div>
                            </div>
                        </div>
                    </div>
                    """, 
                    viewerId, 
                    glbPath));
            }
        } catch (Exception e) {
            // FIXME add me later log.error("Multiblock GLB processing failed, message: {}", e.getMessage());
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
    
    /**
     * 将 PNG 路径转换为 GLB 路径
     */
    private String convertToGLBPath(String pngPath) {
        return pngPath.substring(0, pngPath.length() - 4) + ".glb";
    }
    
    /**
     * 生成唯一的查看器 ID
     */
    private String generateUniqueViewerId(String prefix) {
        return String.format("glb-viewer-%s-%d-%d", prefix, System.currentTimeMillis(), id++);
    }
    
    /**
     * 添加 GLB 查看器到缓冲区
     */
    private void addGLBViewer(List<String> buffer, String viewerId, String glbPath) {
        buffer.add(String.format("""
            <div class="glb-viewer-container">
                <div id="%s" class="glb-viewer" 
                     data-glb-viewer="../../%s"
                     data-viewer-type="multiblock"
                     data-auto-rotate="true"
                     style="width: 100%%; height: 100%%;">
                    <div class="glb-viewer-loading" style="display: flex; align-items: center; justify-content: center; height: 100%%; background: transparent;">
                        <div class="spinner-border" role="status">
                            <span class="visually-hidden">Loading 3D model...</span>
                        </div>
                    </div>
                </div>
            </div>
            """, 
            viewerId,
            glbPath));
    }
    
    /**
     * 处理多方块页面错误
     */
    private void handleMultiblockError(List<String> buffer, PageMultiblock page) {
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

    private void parseMultiMultiblockPage(List<String> buffer, PageMultiMultiblock page) {
        try {
            // 使用新方法生成多个GLB文件
            List<String> glbPaths = textureRenderer.generateMultiMultiblockGLB(page);
            
            // 添加GLB查看器
            if (!glbPaths.isEmpty()) {
                String viewerId = generateUniqueViewerId("multimultiblock");
                StringBuilder glbPathsJson = new StringBuilder("[");
                
                // 构建GLB文件路径的JSON数组
                for (int i = 0; i < glbPaths.size(); i++) {
                    if (i > 0) {
                        glbPathsJson.append(",");
                    }
                    glbPathsJson.append("\"../../")
                              .append(glbPaths.get(i))
                              .append("\"");
                }
                glbPathsJson.append("]");
                
                // 添加多方块GLB查看器，匹配期望的格式
                buffer.add(String.format("""
                    <div class="glb-viewer-container">
                        <div id="%s" 
                             class="glb-viewer"
                             data-glb-viewers=%s
                             data-viewer-type="multimultiblock"
                             data-auto-rotate="true"
                             data-auto-load="false">
                            <div class="glb-viewer-loading">
                                <div class="spinner-border" role="status">
                                    <span class="visually-hidden">Loading 3D model...</span>
                                </div>
                            </div>
                        </div>
                    </div>
                    """, 
                    viewerId, glbPathsJson));
            }
        } catch (Exception e) {
            // TODO 日志太多暂时移除 log.error("tfc:multimultiblock GLB processing failed", e);
            formatWithTooltip(buffer, localizationManager.translate(I18n.MULTIBLOCK), localizationManager.translate(I18n.MULTIBLOCK_ONLY_IN_GAME));
        }
    }

    /// page_table
    private void parseTablePage(BookEntry entry, List<String> buffer, PageTable page) {
        try {
            formatTable(entry, buffer, page);
        } catch (Exception e) {
            log.error("Table formatting failed for page '{}': {}",
                    page.getTitle() != null ? page.getTitle() : "Unknown", e.getMessage(), e);

            // 渲染一个错误提示，而不是让整个程序失败
            buffer.add("<div class=\"table-error\" style=\"color:#800;padding:15px;border:1px solid #800;border-radius:4px;margin:10px 0;background:#fee;\">");
            buffer.add("<strong>表格渲染错误</strong><br>");
            buffer.add("页面的表格数据格式有误，无法正常显示。请检查相关配置文件。");
            buffer.add("</div>");
        }
    }


    private void formatTable(BookEntry entry, List<String> buffer, PageTable data) {
        List<PageTableString> strings = data.getStrings();
        int configuredColumns = data.getColumns();
        int totalColumns = configuredColumns + 1; // +1 for the first column
        List<PageTableLegend> legend = data.getLegend();

        // 记录原始数据信息
        log.debug("Table data: {} elements, {} configured columns, {} total columns",
                strings.size(), configuredColumns, totalColumns);

        // 检查数据完整性并处理不完整的情况
        if (strings.size() < totalColumns) {
            log.warn("Table data incomplete: expected at least {} elements, got {}. Filling with empty cells.",
                    totalColumns, strings.size());

            // 填充缺失的元素为空
            List<PageTableString> paddedStrings = new ArrayList<>(strings);
            while (paddedStrings.size() < totalColumns) {
                paddedStrings.add(new PageTableString());
            }
            strings = paddedStrings;
        }

        if (strings.size() % totalColumns != 0) {
            log.warn("Table data does not perfectly divide columns: {} elements with {} columns. Trimming excess.",
                    strings.size(), totalColumns);

            // 移除多余的元素，使其能除尽（保留最接近的完整行数）
            int maxRows = strings.size() / totalColumns;
            if (maxRows < 2) {
                log.error("Table has too few elements ({} rows) for proper display", maxRows);
                buffer.add("<div class=\"table-error\" style=\"color:#888;padding:10px;border:1px solid #ddd;margin:10px 0;\">");
                buffer.add("<strong>Table data error</strong> Table has too few elements for proper display。<br>");
                buffer.add(String.format("expected columns:%d，actual columns：%d", totalColumns, strings.size()));
                buffer.add("</div>");
                return;
            }

            // 保留完整的行，丢弃不完整的行
            int trimmedSize = maxRows * totalColumns;
            strings = new ArrayList<>(strings.subList(0, trimmedSize));
            log.info("Trimmed table data to {} elements ({} rows)", trimmedSize, maxRows);
        }

        int rows = strings.size() / totalColumns;

        if (rows <= 1) {
            log.warn("Table has only {} rows, skipping render", rows);
            return;
        }

        List<PageTableString> headers = strings.subList(0, totalColumns);
        List<List<PageTableString>> body = new java.util.ArrayList<>();
        for (int i = 1; i < rows; i++) {
            body.add(strings.subList(i * totalColumns, (i + 1) * totalColumns));
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
                buffer.add(java.lang.String.format(
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
