package io.github.tfgcn.fieldguide.renderer;

import freemarker.template.*;
import io.github.tfgcn.fieldguide.Context;
import io.github.tfgcn.fieldguide.I18n;
import io.github.tfgcn.fieldguide.Versions;
import io.github.tfgcn.fieldguide.patchouli.BookCategory;
import io.github.tfgcn.fieldguide.patchouli.BookEntry;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class HtmlRenderer {
    private final Configuration cfg;
    private final String outputDir;
    
    public HtmlRenderer(String templateDir, String outputDir) throws IOException {
        this.outputDir = outputDir;
        
        Files.createDirectories(Paths.get(outputDir));

        // 配置 FreeMarker
        cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setDirectoryForTemplateLoading(new File(templateDir));
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setAutoEscapingPolicy(Configuration.DISABLE_AUTO_ESCAPING_POLICY);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setFallbackOnNullLoopVariable(false);
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

    public void generatePage(String templateName, String outputDir, String outputFileName, Map<String, Object> data)
            throws IOException, TemplateException {
        
        Template template = cfg.getTemplate(templateName);
        Path outputPath = Paths.get(outputDir, outputFileName);

        FileUtils.createParentDirectories(outputPath.toFile());
        try (Writer out = new OutputStreamWriter(
                Files.newOutputStream(outputPath), StandardCharsets.UTF_8)) {
            template.process(data, out);
        }
    }

    private static String getSplashLocation() {
        return "splash";
    }


    public void copyStaticFiles(Context context) throws IOException {

        // copy files from assets/static to outputDir
        FileUtils.copyDirectory(new File("assets/static"), new File(context.getOutputRootDir() + "/static"));
        // copy files from assets/textures to outputDir/_images
        FileUtils.copyDirectory(new File("assets/textures"), new File(context.getOutputRootDir() + "/_images"));
        // Always copy the redirect, which defaults to en_us/
        FileUtils.copyFile(new File("assets/templates/redirect.html"), new File(context.getOutputRootDir() + "/index.html"));
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
        data.put("root", "..");// context.getRootDir()

        // text
        data.put("text_index", context.translate(I18n.INDEX));
        data.put("text_contents", context.translate(I18n.CONTENTS));
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
        data.put("text_github", context.translate(I18n.GITHUB));
        data.put("text_discord", context.translate(I18n.DISCORD));
        data.put("current_lang_key", context.getLang());
        data.put("current_lang", context.translate(String.format(I18n.LANGUAGE_NAME, context.getLang())));
        data.put("langs", generateCategoryLanguageLinks(Versions.LANGUAGES, context, categoryId));
        data.put("index", "../");
        data.put("root", "../..");// context.getRootDir()
        data.put("location", generateCategoryBreadcrumb("../", cat.getName()));
        data.put("contents", generateCategoryTableOfContents(context.getSortedCategories(), categoryId));
        data.put("page_content", generateCategoryPageContent(cat));

        // 生成分类页面
        String outputDir = Paths.get(context.getOutputDir(), categoryId).toString();
        context.getHtmlRenderer().generatePage("index.ftl", outputDir, "index.html", data);

        // 生成该分类下的条目页面
        buildEntryPages(context, categoryId, cat);
    }


    public static String generateLanguageDropdown(List<String> languages, Context context) {
        if (languages == null || languages.isEmpty()) {
            return "";
        }

        return languages.stream()
                .filter(Objects::nonNull)
                .map(lang -> String.format(
                        """
                        <a href="../%s/index.html" class="dropdown-item">%s</a>
                        """,
                        lang,
                        context.translate(String.format(I18n.LANGUAGE_NAME, lang))
                ))
                .collect(Collectors.joining("\n"));
    }

    public static String indexBreadcrumbModern(String relativePath) {
        String iconHtml = "<i class=\"bi bi-house-fill\"></i>";

        if (relativePath == null || relativePath.trim().isEmpty()) {
            return "<li class=\"breadcrumb-item\">" + iconHtml + "</li>";
        } else {
            return """
                <li class="breadcrumb-item">
                    <a href="%s">%s</a>
                </li>
                """.formatted(relativePath, iconHtml);
        }
    }

    public static String generateTableOfContents(List<Map.Entry<String, BookCategory>> sortedCategories) {
        return sortedCategories.stream()
                .map(entry -> {
                    String catId = entry.getKey();
                    BookCategory category = entry.getValue();
                    return String.format(
                            """
                            <li><a href="./%s/">%s</a></li>
                            """,
                            catId, category.getName()
                    );
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * 生成主页内容
     */
    public static String generateHomePageContent(Context context, List<Map.Entry<String, BookCategory>> sortedCategories) {
        String splashImage = getSplashLocation();
        String textHome = context.translate(I18n.HOME);
        String textEntries = context.translate(I18n.CATEGORIES);

        // 生成分类卡片
        String categoryCards = sortedCategories.stream()
                .map(entry -> {
                    String catId = entry.getKey();
                    BookCategory category = entry.getValue();
                    return String.format(
                            """
                            <div class="col">
                                <div class="card">
                                    <div class="card-header">
                                        <a href="%s/index.html">%s</a>
                                    </div>
                                    <div class="card-body">%s</div>
                                </div>
                            </div>
                            """,
                            catId, category.getName(), category.getDescription()
                    );
                })
                .collect(Collectors.joining("\n"));

        return String.format(
                """
                <!-- START -->
                <div align="center">
                  <a href="https://discord.gg/AEaCzCTUwQ">
                    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3.1.2/assets/compact/social/discord-singular_vector.svg" alt="Chat on Discord">
                  </a>
                  <a href="https://www.curseforge.com/members/terrafirmagreg/projects">
                    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges/assets/compact/available/curseforge_vector.svg" alt="Available on СurseForge">
                  </a>
                  <br/>
                </div>
                <br/>
                <img class="d-block w-200 mx-auto mb-3 img-fluid" src="../_images/%s.png" alt="TerraFirmaCraft Field Guide Splash Image">
                <p>%s</p>
                <p><strong>%s</strong></p>
                <div class="row row-cols-1 row-cols-md-2 g-3">
                %s
                </div><!-- END -->
                """,
                splashImage, textHome, textEntries, categoryCards
        );
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
                                    BookEntry bookEntry = subEntry.getValue();
                                    return String.format(
                                            """
                                            <li><a href="./%s.html">%s</a></li>
                                            """,
                                            bookEntry.getRelId(), bookEntry.getName()
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
                            entryCardWithDefaultIcon(bookEntry.getRelId(), bookEntry.getName(),
                                    bookEntry.getIconPath(), bookEntry.getIconName())
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
            data.put("text_github", context.translate(I18n.GITHUB));
            data.put("text_discord", context.translate(I18n.DISCORD));
            data.put("current_lang_key", context.getLang());
            data.put("current_lang", context.translate(String.format(I18n.LANGUAGE_NAME, context.getLang())));
            data.put("langs", generateEntryLanguageLinks(Versions.LANGUAGES, context, categoryId, entry.getRelId()));
            data.put("index", "../");
            data.put("root", "../..");// context.getRootDir()
            data.put("location", generateEntryBreadcrumb("../", cat.getName(), entry.getName()));
            data.put("contents", generateEntryTableOfContents(context.getSortedCategories(), categoryId, entryId));
            data.put("page_content", generateEntryPageContent(entry));

            // 生成条目页面
            String outputFileName = categoryId + "/" + entry.getRelId() + ".html";
            context.getHtmlRenderer().generatePage("index.ftl", context.getOutputDir(), outputFileName, data);
        }
    }

    private static String cleanImagePath(String iconPath) {
        if (iconPath == null) return "";
        return iconPath.replace("../../_images/", "").replace("..\\..\\_images\\", "");
    }

    private static String generateEntryLanguageLinks(List<String> languages, Context context, String categoryId, String relId) {
        return languages.stream()
                .map(lang -> String.format(
                        """
                        <a href="../../%s/%s/%s.html" class="dropdown-item">%s</a>
                        """,
                        lang, categoryId, relId, context.translate(String.format(I18n.LANGUAGE_NAME, lang))
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
                                    boolean isCurrent = entryId.equals(currentEntryId);

                                    return String.format(
                                            """
                                            <li><a href="./%s.html"%s>%s</a></li>
                                            """,
                                            bookEntry.getRelId(),
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
        String titleWithIcon = titleWithOptionalIcon(entry.getName(), entry.getIconPath(), entry.getIconName());
        String innerContent = String.join("", entry.getBuffer());

        return String.format(
                """
                <h1 class="d-flex align-items-center mb-4">%s</h1>
                %s
                """,
                titleWithIcon, innerContent
        );
    }

    private static String entryCardWithDefaultIcon(String entryPath, String entryTitle, String iconPath, String iconName) {
        String iconSrc;
        if (iconPath == null || iconPath.isEmpty()) {
            iconSrc = "../../_images/placeholder_16.png";
        } else {
            iconSrc = iconPath;
        }

        return String.format(
                """
                <div class="card-body">
                    <div class="d-flex align-items-center">
                        <img class="entry-card-icon me-2" src="%s" alt="%s" />
                        <a href="%s.html">%s</a>
                    </div>
                </div>
                """,
                iconSrc, iconName != null ? iconName : entryTitle, entryPath, entryTitle
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