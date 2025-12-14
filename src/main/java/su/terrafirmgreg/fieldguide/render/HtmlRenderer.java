package su.terrafirmgreg.fieldguide.render;

import freemarker.template.*;
import su.terrafirmgreg.fieldguide.Constants;
import su.terrafirmgreg.fieldguide.Context;
import su.terrafirmgreg.fieldguide.data.patchouli.BookCategory;
import su.terrafirmgreg.fieldguide.data.patchouli.BookEntry;
import su.terrafirmgreg.fieldguide.gson.JsonUtils;
import su.terrafirmgreg.fieldguide.localization.I18n;
import su.terrafirmgreg.fieldguide.localization.Language;
import su.terrafirmgreg.fieldguide.localization.LocalizationManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
public class HtmlRenderer {
    private static final Pattern SEARCH_STRIP_PATTERN = Pattern.compile("\\$\\([^)]*\\)");

    private final Configuration cfg;

    private final LocalizationManager localizationManager;

    private final String outputRootDir;

    public HtmlRenderer(LocalizationManager localizationManager, String outputRootDir) throws IOException {
        this.localizationManager = localizationManager;
        this.outputRootDir = outputRootDir;

        cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setDirectoryForTemplateLoading(new File(Constants.TEMPLATE_DIR));
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setAutoEscapingPolicy(Configuration.DISABLE_AUTO_ESCAPING_POLICY);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setFallbackOnNullLoopVariable(false);
    }

    public void buildBookHtml(Context context) throws Exception {

        // copy files from assets/static to outputDir
        copyStaticFiles();

        // Home page
        buildHomePage(context.getCategories());

        // Search page
        buildSearchPage(context.getCategories());

        saveSearchData(context.getSearchTree());

        // Category pages
        for (BookCategory category : context.getCategories()) {
            buildCategoryPage(category, context.getCategories());
        }

        System.out.println("Static site generated successfully!");
    }

    public void generatePage(String templateName, String outputFileName, Map<String, Object> data)
            throws IOException, TemplateException {
        
        Template template = cfg.getTemplate(templateName);
        Path outputPath = Paths.get(outputRootDir, localizationManager.getCurrentLanguage().getKey(), outputFileName);

        FileUtils.createParentDirectories(outputPath.toFile());
        try (Writer out = new OutputStreamWriter(Files.newOutputStream(outputPath), StandardCharsets.UTF_8)) {
            template.process(data, out);
        }
    }

    public void copyStaticFiles() throws IOException {

        // copy files from assets/static to outputDir
        FileUtils.deleteDirectory(new File(outputRootDir + "/static"));
        FileUtils.copyDirectory(new File("assets/static"), new File(outputRootDir + "/static"));

        // copy files from assets/textures to outputDir/_images
        FileUtils.copyDirectory(new File("assets/textures"), new File(outputRootDir + "/_images"));
        // Always copy the redirect, which defaults to en_us/
        FileUtils.copyFile(new File("assets/templates/redirect.html"), new File(outputRootDir + "/index.html"));
    }

    public void buildHomePage(List<BookCategory> categories) throws IOException, TemplateException {
        Map<String, Object> data = new HashMap<>();
        // meta
        data.put("title", localizationManager.translate(I18n.TITLE));
        data.put("long_title", localizationManager.translate(I18n.TITLE) + " | " + Constants.MC_VERSION);
        data.put("short_description", localizationManager.translate(I18n.HOME));
        data.put("preview_image", "splash.png");
        data.put("root", "..");

        // text
        data.put("text_index", localizationManager.translate(I18n.INDEX));
        data.put("text_home", localizationManager.translate(I18n.HOME));
        data.put("text_github", localizationManager.translate(I18n.GITHUB));
        data.put("text_discord", localizationManager.translate(I18n.DISCORD));
        data.put("text_categories", localizationManager.translate(I18n.CATEGORIES));
        data.put("text_contents", localizationManager.translate(I18n.CONTENTS));

        // langs and navigation
        data.put("current_lang", localizationManager.getCurrentLanguage());
        data.put("languages", Language.asList());
        data.put("index", "#");

        // contents
        data.put("categories", categories);

        // generate page
        generatePage("home.ftl", "index.html", data);
    }

    public void buildSearchPage(List<BookCategory> categories) throws IOException, TemplateException {
        Map<String, Object> data = new HashMap<>();
        // meta
        data.put("title", localizationManager.translate(I18n.TITLE));
        data.put("long_title", localizationManager.translate(I18n.TITLE) + " | " + Constants.MC_VERSION);
        data.put("short_description", localizationManager.translate(I18n.HOME));
        data.put("preview_image", "splash.png");
        data.put("root", "..");

        // text
        data.put("text_index", localizationManager.translate(I18n.INDEX));
        data.put("text_contents", localizationManager.translate(I18n.CONTENTS));
        data.put("text_github", localizationManager.translate(I18n.GITHUB));
        data.put("text_discord", localizationManager.translate(I18n.DISCORD));

        // langs and navigation
        data.put("current_lang", localizationManager.getCurrentLanguage());
        data.put("languages", Language.asList());
        data.put("index", "./");

        // contents
        data.put("categories", categories);

        // generate page
        generatePage("search.ftl", "search.html", data);
    }

    public void saveSearchData(List<Map<String, String>> searchTree) throws IOException {

        for (Map<String, String> result : searchTree) {
            String originalContent = result.get("content");
            String content = searchStrip(originalContent);
            result.put("content", content);
        }
        JsonUtils.writeFile(new File(outputRootDir + "/" + localizationManager.getCurrentLanguage().getKey() + "/search_index.json"), searchTree);
    }

    public void buildCategoryPage(BookCategory cat, List<BookCategory> categories) throws IOException, TemplateException {
        Map<String, Object> data = new HashMap<>();
        data.put("title", localizationManager.translate(I18n.TITLE));
        data.put("long_title", cat.getName() + " | " + localizationManager.translate(I18n.SHORT_TITLE));
        data.put("short_description", cat.getName());
        data.put("preview_image", "splash.png");
        data.put("root", "..");

        data.put("text_index", localizationManager.translate(I18n.INDEX));
        data.put("text_contents", localizationManager.translate(I18n.CONTENTS));
        data.put("text_github", localizationManager.translate(I18n.GITHUB));
        data.put("text_discord", localizationManager.translate(I18n.DISCORD));

        data.put("current_lang", localizationManager.getCurrentLanguage());
        data.put("languages", Language.asList());
        data.put("index", "./");

        data.put("categories", categories);
        data.put("current_category", cat);

        // 生成分类页面
        generatePage("category.ftl", cat.getId() + ".html", data);

        // 生成该分类下的条目页面
        buildEntryPages(cat, categories);
    }

    private void buildEntryPages(BookCategory cat, List<BookCategory> categories) throws IOException, TemplateException {
        for (BookEntry entry : cat.getEntries()) {
            Map<String, Object> data = new HashMap<>();
            data.put("title", localizationManager.translate(I18n.TITLE));
            data.put("long_title", entry.getName() + " | " + localizationManager.translate(I18n.SHORT_TITLE));
            data.put("short_description", entry.getName());
            data.put("preview_image", cleanImagePath(entry.getIconPath()));
            data.put("root", "../..");

            data.put("text_index", localizationManager.translate(I18n.INDEX));
            data.put("text_contents", localizationManager.translate(I18n.CONTENTS));
            data.put("text_github", localizationManager.translate(I18n.GITHUB));
            data.put("text_discord", localizationManager.translate(I18n.DISCORD));

            data.put("current_lang", localizationManager.getCurrentLanguage());
            data.put("languages", Language.asList());
            data.put("index", "../");

            data.put("categories", categories);
            data.put("current_category", cat);
            data.put("current_entry", entry);

            // 生成条目页面
            generatePage("entry.ftl", entry.getId() + ".html", data);
        }
    }

    /**
     * 清理搜索文本，移除 $(...) 模式
     */
    public static String searchStrip(String input) {
        return SEARCH_STRIP_PATTERN.matcher(input).replaceAll("");
    }


    private static String cleanImagePath(String iconPath) {
        if (iconPath == null) return "";
        return iconPath.replace("../../", "").replace("..\\..\\", "");
    }
}