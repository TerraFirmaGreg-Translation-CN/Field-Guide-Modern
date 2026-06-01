package team.terrafirmgreg.fieldguide.site;

import freemarker.template.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import team.terrafirmgreg.fieldguide.Constants;
import team.terrafirmgreg.fieldguide.data.patchouli.Book;
import team.terrafirmgreg.fieldguide.data.patchouli.BookCategory;
import team.terrafirmgreg.fieldguide.data.patchouli.BookEntry;
import team.terrafirmgreg.fieldguide.gson.JsonUtils;
import team.terrafirmgreg.fieldguide.localization.I18n;
import team.terrafirmgreg.fieldguide.localization.Language;
import team.terrafirmgreg.fieldguide.localization.LocalizationManager;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

@Slf4j
public class SiteRenderer {
    private static final Pattern SEARCH_STRIP_PATTERN = Pattern.compile("\\$\\([^)]*\\)");

    private final Configuration cfg;
    private final LocalizationManager localizationManager;
    private final String outputRootDir;

    public SiteRenderer(LocalizationManager localizationManager, String outputRootDir) throws IOException {
        this.localizationManager = localizationManager;
        this.outputRootDir = outputRootDir;

        cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setAutoEscapingPolicy(Configuration.DISABLE_AUTO_ESCAPING_POLICY);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setFallbackOnNullLoopVariable(false);
    }

    public void generate(Book book) throws Exception {
        buildHomePage(book.getCategories());
        buildSearchPage(book.getCategories());
        saveSearchData(collectSearchTree(book.getCategories()));

        for (BookCategory category : book.getCategories()) {
            buildCategoryPage(category, book.getCategories());
        }
        log.info("Static site generated under {}", outputRootDir);
    }

    private List<Map<String, String>> collectSearchTree(List<BookCategory> categories) {
        List<Map<String, String>> searchData = new ArrayList<>();
        for (BookCategory category : categories) {
            for (BookEntry entry : category.getEntries()) {
                searchData.addAll(entry.getSearchTree());
            }
        }
        return searchData;
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
        Path staticOut = Paths.get(outputRootDir, "static");
        FileUtils.deleteDirectory(staticOut.toFile());
        copyResourceDir("static", staticOut);
        copyRecipeUiImages();

        Path redirectDest = Paths.get(outputRootDir, "index.html");
        if (copyClasspathResource("templates/redirect.html", redirectDest)) {
            log.debug("Wrote root redirect from classpath to {}", redirectDest);
        }
    }

    /**
     * Recipe frames, placeholders, knapping overlay, etc. (same as legacy CLI:
     * {@code assets/textures} → {@code _images/} at site root).
     */
    private void copyRecipeUiImages() throws IOException {
        Path textures = Paths.get("assets/textures");
        if (!Files.isDirectory(textures)) {
            log.warn("Missing assets/textures — crafting recipe backgrounds will be broken ({})", textures.toAbsolutePath());
            return;
        }
        Path dest = Paths.get(outputRootDir, "_images");
        if (Files.exists(dest)) {
            FileUtils.deleteDirectory(dest.toFile());
        }
        FileUtils.copyDirectory(textures.toFile(), dest.toFile());
        log.info("Copied recipe UI images to {}/_images", outputRootDir);
    }

    public void copyGeneratedIcons(Path exportRoot) throws IOException {
        Path assetsIcons = exportRoot.resolve("assets/icons");
        Path legacyGenerated = exportRoot.resolve("generated");
        Path srcIcons = Files.isDirectory(assetsIcons) ? assetsIcons : legacyGenerated.resolve("icons");
        if (!Files.isDirectory(srcIcons)) {
            log.warn("No handbook icons under {} (expected assets/icons or generated/icons)", exportRoot);
            return;
        }
        Path destIcons = Paths.get(outputRootDir, "generated", "icons");
        if (Files.exists(destIcons)) {
            FileUtils.deleteDirectory(destIcons.toFile());
        }
        FileUtils.copyDirectory(srcIcons.toFile(), destIcons.toFile());
        log.info("Copied handbook icons to {}", destIcons);
    }

    private void copyResourceDir(String resourceName, Path dest) throws IOException {
        URL url = getClass().getClassLoader().getResource(resourceName);
        if (url == null) {
            log.warn("Missing classpath resource: {}", resourceName);
            return;
        }
        if ("file".equals(url.getProtocol())) {
            try {
                Path src = Paths.get(url.toURI());
                if (Files.isDirectory(src)) {
                    FileUtils.copyDirectory(src.toFile(), dest.toFile());
                }
            } catch (java.net.URISyntaxException e) {
                throw new IOException("Bad file resource URL for " + resourceName, e);
            }
            return;
        }
        if ("jar".equals(url.getProtocol())) {
            copyResourceTreeFromJar(url, resourceName, dest);
            return;
        }
        log.warn("Unsupported classpath URL for {}: {}", resourceName, url);
    }

    /** Copies a single classpath file (works inside fat jars). */
    private boolean copyClasspathResource(String resourceName, Path dest) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                return false;
            }
            Files.createDirectories(dest.getParent());
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            return true;
        }
    }

    private static void copyResourceTreeFromJar(URL jarResourceUrl, String resourcePrefix, Path dest)
            throws IOException {
        if (!(jarResourceUrl.openConnection() instanceof JarURLConnection connection)) {
            throw new IOException("Not a jar resource URL: " + jarResourceUrl);
        }
        String prefix = resourcePrefix.endsWith("/") ? resourcePrefix : resourcePrefix + "/";
        try (JarFile jar = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(prefix) || entry.isDirectory()) {
                    continue;
                }
                String relative = name.substring(prefix.length());
                Path target = dest.resolve(relative);
                Files.createDirectories(target.getParent());
                try (InputStream in = jar.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public void buildHomePage(List<BookCategory> categories) throws IOException, TemplateException {
        Map<String, Object> data = basePageData("..");
        data.put("text_index", localizationManager.translate(I18n.INDEX));
        data.put("text_home", localizationManager.translate(I18n.HOME));
        data.put("text_github", localizationManager.translate(I18n.GITHUB));
        data.put("text_discord", localizationManager.translate(I18n.DISCORD));
        data.put("text_categories", localizationManager.translate(I18n.CATEGORIES));
        data.put("text_contents", localizationManager.translate(I18n.CONTENTS));
        data.put("index", "#");
        data.put("categories", categories);
        generatePage("home.ftl", "index.html", data);
    }

    public void buildSearchPage(List<BookCategory> categories) throws IOException, TemplateException {
        Map<String, Object> data = basePageData("..");
        data.put("text_index", localizationManager.translate(I18n.INDEX));
        data.put("text_contents", localizationManager.translate(I18n.CONTENTS));
        data.put("text_github", localizationManager.translate(I18n.GITHUB));
        data.put("text_discord", localizationManager.translate(I18n.DISCORD));
        data.put("index", "./");
        data.put("categories", categories);
        generatePage("search.ftl", "search.html", data);
    }

    public void saveSearchData(List<Map<String, String>> searchTree) throws IOException {
        for (Map<String, String> result : searchTree) {
            result.put("content", searchStrip(result.get("content")));
        }
        JsonUtils.writeFile(
                Paths.get(outputRootDir, localizationManager.getCurrentLanguage().getKey(), "search_index.json").toFile(),
                searchTree);
    }

    public void buildCategoryPage(BookCategory cat, List<BookCategory> categories) throws IOException, TemplateException {
        Map<String, Object> data = basePageData("..");
        data.put("long_title", cat.getName() + " | " + localizationManager.translate(I18n.SHORT_TITLE));
        data.put("short_description", cat.getName());
        data.put("index", "./");
        data.put("categories", categories);
        data.put("current_category", cat);
        generatePage("category.ftl", cat.getId() + ".html", data);
        buildEntryPages(cat, categories);
    }

    private void buildEntryPages(BookCategory cat, List<BookCategory> categories) throws IOException, TemplateException {
        for (BookEntry entry : cat.getEntries()) {
            Map<String, Object> data = basePageData("../..");
            data.put("long_title", entry.getName() + " | " + localizationManager.translate(I18n.SHORT_TITLE));
            data.put("short_description", entry.getName());
            data.put("preview_image", cleanImagePath(entry.getIconPath()));
            data.put("index", "../");
            data.put("categories", categories);
            data.put("current_category", cat);
            data.put("current_entry", entry);
            generatePage("entry.ftl", entry.getId() + ".html", data);
        }
    }

    private Map<String, Object> basePageData(String root) {
        Map<String, Object> data = new HashMap<>();
        data.put("generatedRoot", generatedRoot(root));
        data.put("title", localizationManager.translate(I18n.TITLE));
        data.put("long_title", localizationManager.translate(I18n.TITLE) + " | " + Constants.MC_VERSION);
        data.put("short_description", localizationManager.translate(I18n.HOME));
        data.put("preview_image", "splash.png");
        data.put("root", root);
        data.put("text_index", localizationManager.translate(I18n.INDEX));
        data.put("text_contents", localizationManager.translate(I18n.CONTENTS));
        data.put("text_github", localizationManager.translate(I18n.GITHUB));
        data.put("text_discord", localizationManager.translate(I18n.DISCORD));
        data.put("current_lang", localizationManager.getCurrentLanguage());
        data.put("languages", Language.asList());
        return data;
    }

    public static String searchStrip(String input) {
        return SEARCH_STRIP_PATTERN.matcher(input).replaceAll("");
    }

    private static String generatedRoot(String root) {
        return root + "/../generated";
    }

    private static String cleanImagePath(String iconPath) {
        if (iconPath == null) {
            return "";
        }
        return iconPath.replace("../../", "").replace("..\\..\\", "");
    }
}
