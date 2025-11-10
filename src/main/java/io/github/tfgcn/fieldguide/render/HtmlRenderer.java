package io.github.tfgcn.fieldguide.render;

import freemarker.template.*;
import io.github.tfgcn.fieldguide.Context;
import io.github.tfgcn.fieldguide.I18n;
import io.github.tfgcn.fieldguide.book.BookCategory;
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

    private static String getSplashLocation() {
        return "splash";
    }
}