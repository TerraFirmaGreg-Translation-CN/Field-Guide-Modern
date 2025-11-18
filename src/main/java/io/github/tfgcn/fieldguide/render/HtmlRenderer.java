package io.github.tfgcn.fieldguide.render;

import freemarker.template.*;
import io.github.tfgcn.fieldguide.*;
import io.github.tfgcn.fieldguide.gson.JsonUtils;
import io.github.tfgcn.fieldguide.data.patchouli.BookCategory;
import io.github.tfgcn.fieldguide.data.patchouli.BookEntry;
import io.github.tfgcn.fieldguide.html.LanguageDropdown;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class HtmlRenderer {
    private static final Pattern SEARCH_STRIP_PATTERN = Pattern.compile("\\$\\([^)]*\\)");

    private final Configuration cfg;

    public HtmlRenderer(String templateDir) throws IOException {
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
        buildSearchPage(context);

        // Category pages
        for (BookCategory category : context.getCategories()) {
            String categoryId = category.getId();
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


    public void copyStaticFiles(Context context) throws IOException {

        // copy files from assets/static to outputDir
        FileUtils.deleteDirectory(new File(context.getOutputRootDir() + "/static"));
        FileUtils.copyDirectory(new File("assets/static"), new File(context.getOutputRootDir() + "/static"));

        // copy files from assets/textures to outputDir/_images
        FileUtils.copyDirectory(new File("assets/textures"), new File(context.getOutputRootDir() + "/_images"));
        // Always copy the redirect, which defaults to en_us/
        FileUtils.copyFile(new File("assets/templates/redirect.html"), new File(context.getOutputRootDir() + "/index.html"));
    }

    public void buildHomePage(Context context) throws IOException, TemplateException {
        Map<String, Object> data = new HashMap<>();
        // meta
        data.put("title", context.translate(I18n.TITLE));
        data.put("long_title", context.translate(I18n.TITLE) + " | " + Constants.MC_VERSION);
        data.put("short_description", context.translate(I18n.HOME));
        data.put("preview_image", "splash.png");
        data.put("root", "..");// context.getBasePath()

        // text
        data.put("text_index", context.translate(I18n.INDEX));
        data.put("text_home", context.translate(I18n.HOME));
        data.put("text_github", context.translate(I18n.GITHUB));
        data.put("text_discord", context.translate(I18n.DISCORD));
        data.put("text_categories", context.translate(I18n.CATEGORIES));
        data.put("text_contents", context.translate(I18n.CONTENTS));

        // langs and navigation
        data.put("current_lang_key", context.getLang());
        data.put("current_lang", context.translate(String.format(I18n.LANGUAGE_NAME, context.getLang())));
        data.put("languages", getLanguageDropdown(Constants.LANGUAGES, context));
        data.put("index", "#");

        // contents
        data.put("categories", context.getCategories());

        // generate page
        context.getHtmlRenderer().generatePage("home.ftl", context.getOutputLangDir(), "index.html", data);
    }

    public void buildSearchPage(Context context) throws IOException, TemplateException {
        Map<String, Object> data = new HashMap<>();
        // meta
        data.put("title", context.translate(I18n.TITLE));
        data.put("long_title", context.translate(I18n.TITLE) + " | " + Constants.MC_VERSION);
        data.put("short_description", context.translate(I18n.HOME));
        data.put("preview_image", "splash.png");
        data.put("root", "..");// basePath

        // text
        data.put("text_index", context.translate(I18n.INDEX));
        data.put("text_contents", context.translate(I18n.CONTENTS));
        data.put("text_github", context.translate(I18n.GITHUB));
        data.put("text_discord", context.translate(I18n.DISCORD));

        // langs and navigation
        data.put("current_lang_key", context.getLang());
        data.put("current_lang", context.translate(String.format(I18n.LANGUAGE_NAME, context.getLang())));
        data.put("languages", getLanguageDropdown(Constants.LANGUAGES, context));
        data.put("index", "./");

        // contents
        data.put("categories", context.getCategories());

        // generate page
        context.getHtmlRenderer().generatePage("search.ftl", context.getOutputLangDir(), "search.html", data);

        for (Map<String, String> result : context.getSearchTree()) {
            String originalContent = result.get("content");
            String content = searchStrip(originalContent);
            result.put("content", content);
        }
        JsonUtils.writeFile(new File(context.getOutputLangDir() + "/search_index.json"), context.getSearchTree());
    }

    public void buildCategoryPage(Context context, String categoryId, BookCategory cat) throws IOException, TemplateException {
        Map<String, Object> data = new HashMap<>();
        data.put("title", context.translate(I18n.TITLE));
        data.put("long_title", cat.getName() + " | " + context.translate(I18n.SHORT_TITLE));
        data.put("short_description", cat.getName());
        data.put("preview_image", "splash.png");
        data.put("root", "../..");

        data.put("text_index", context.translate(I18n.INDEX));
        data.put("text_contents", context.translate(I18n.CONTENTS));
        data.put("text_github", context.translate(I18n.GITHUB));
        data.put("text_discord", context.translate(I18n.DISCORD));

        data.put("current_lang_key", context.getLang());
        data.put("current_lang", context.translate(String.format(I18n.LANGUAGE_NAME, context.getLang())));
        data.put("languages", getLanguageDropdown(Constants.LANGUAGES, context));
        data.put("index", "../");

        data.put("categories", context.getCategories());
        data.put("current_category", cat);

        // 生成分类页面
        String outputDir = Paths.get(context.getOutputLangDir(), categoryId).toString();
        context.getHtmlRenderer().generatePage("category.ftl", outputDir, "index.html", data);

        // 生成该分类下的条目页面
        buildEntryPages(context, categoryId, cat);
    }

    private void buildEntryPages(Context context, String categoryId, BookCategory cat) throws IOException, TemplateException {
        for (BookEntry entry : cat.getEntries()) {
            Map<String, Object> data = new HashMap<>();
            data.put("title", context.translate(I18n.TITLE));
            data.put("long_title", entry.getName() + " | " + context.translate(I18n.SHORT_TITLE));
            data.put("short_description", entry.getName());
            data.put("preview_image", cleanImagePath(entry.getIconPath()));
            data.put("root", "../..");

            data.put("text_index", context.translate(I18n.INDEX));
            data.put("text_contents", context.translate(I18n.CONTENTS));
            data.put("text_github", context.translate(I18n.GITHUB));
            data.put("text_discord", context.translate(I18n.DISCORD));

            data.put("current_lang_key", context.getLang());
            data.put("current_lang", context.translate(String.format(I18n.LANGUAGE_NAME, context.getLang())));
            data.put("languages", getLanguageDropdown(Constants.LANGUAGES, context));
            data.put("index", "../");

            data.put("categories", context.getCategories());
            data.put("current_category", cat);
            data.put("current_entry", entry);

            data.put("page_content", generateEntryPageContent(entry));

            // 生成条目页面
            String outputFileName = categoryId + "/" + entry.getRelId() + ".html";
            context.getHtmlRenderer().generatePage("entry.ftl", context.getOutputLangDir(), outputFileName, data);
        }
    }

    /**
     * 清理搜索文本，移除 $(...) 模式
     */
    public static String searchStrip(String input) {
        return SEARCH_STRIP_PATTERN.matcher(input).replaceAll("");
    }

    public static List<LanguageDropdown> getLanguageDropdown(List<String> languages, Context context) {
        if (languages == null || languages.isEmpty()) {
            return null;
        }

        return languages.stream()
                .filter(Objects::nonNull)
                .map(lang -> new LanguageDropdown(lang, context.translate(String.format(I18n.LANGUAGE_NAME, lang))))
                .collect(Collectors.toList());
    }

    private static String cleanImagePath(String iconPath) {
        if (iconPath == null) return "";
        return iconPath.replace("../../_images/", "").replace("..\\..\\_images\\", "");
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

    private static String titleWithOptionalIcon(String text, String iconSrc, String iconName) {
        if (iconSrc != null && !iconSrc.isEmpty()) {
            return String.format(
                    """
                    <img class="icon-title me-3" src="%s" alt="%s" title="%s" ><span>%s</span>
                    """,
                    iconSrc, text, iconName != null ? iconName : text, text
            );
        } else {
            return text;
        }
    }
}