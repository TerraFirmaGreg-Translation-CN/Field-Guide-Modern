package team.terrafirmgreg.fieldguide.site;

import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import team.terrafirmgreg.fieldguide.Constants;
import team.terrafirmgreg.fieldguide.asset.ItemImageResult;
import team.terrafirmgreg.fieldguide.data.patchouli.Book;
import team.terrafirmgreg.fieldguide.data.patchouli.BookCategory;
import team.terrafirmgreg.fieldguide.data.patchouli.BookEntry;
import team.terrafirmgreg.fieldguide.data.patchouli.BookPage;
import team.terrafirmgreg.fieldguide.exception.InternalException;
import team.terrafirmgreg.fieldguide.export.BlockstateRefResolver;
import team.terrafirmgreg.fieldguide.export.ExportBundle;
import team.terrafirmgreg.fieldguide.export.ExportModelLoader;
import team.terrafirmgreg.fieldguide.export.MultiblockRenderResolver;
import team.terrafirmgreg.fieldguide.localization.Language;
import team.terrafirmgreg.fieldguide.localization.LocalizationManager;
import team.terrafirmgreg.fieldguide.render.IconMarkup;
import team.terrafirmgreg.fieldguide.render.PageRenderer;
import team.terrafirmgreg.fieldguide.render.TextFormatter;
import team.terrafirmgreg.fieldguide.render.TextureRenderer;
import team.terrafirmgreg.fieldguide.site.emi.EmiRecipeIndex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static team.terrafirmgreg.fieldguide.Constants.FIELD_GUIDE;

@Slf4j
public class SiteGenerator implements Callable<Integer> {

    @CommandLine.Option(
            names = {"-e", "--export-dir"},
            required = true,
            description = "guide-export root (manifest.json + assets/ + data/)"
    )
    String exportDir;

    @CommandLine.Option(
            names = {"-o", "--output-dir"},
            description = "Static site output directory",
            defaultValue = "./dist"
    )
    String outputDir;

    @CommandLine.Option(
            names = {"--locales"},
            description = "Comma-separated locale codes (default: all in export)",
            split = ","
    )
    List<String> locales;

    @CommandLine.Option(
            names = {"--emi-dir"},
            description = "EMI export bundle root (bundle.json + recipes/); default: <export-dir>/../emi"
    )
    String emiDir;

    @CommandLine.Option(
            names = {"--recipe-book-base-url"},
            description = "External recipe viewer base URL for EMI item/tag links (empty = disable)",
            defaultValue = SiteRenderer.DEFAULT_RECIPE_BOOK_BASE_URL
    )
    String recipeBookBaseUrl;

    public static void main(String[] args) {
        int code = new CommandLine(new SiteGenerator()).execute(args);
        System.exit(code);
    }

    @Override
    public Integer call() throws Exception {
        Path export = Paths.get(exportDir).toAbsolutePath().normalize();
        Path output = Paths.get(outputDir).toAbsolutePath().normalize();
        log.info("Generating site from export={}, output={}", export, output);

        ExportBundle bundle = ExportBundle.open(export);
        ExportModelLoader models = bundle.getAssets().getModels();
        models.setOutputDir(output);

        Path emiRoot = resolveEmiRoot(export);
        EmiRecipeIndex emiIndex = EmiRecipeIndex.load(emiRoot);

        ExportLocalizationManager l10n = new ExportLocalizationManager(bundle.getLangs());
        BlockstateRefResolver blockstateRefs =
                new BlockstateRefResolver(bundle.getAssets(), bundle.getTagMembers());
        MultiblockRenderResolver multiblockResolver =
                new MultiblockRenderResolver(bundle.getMultiblocks(), blockstateRefs);
        TextureRenderer textureRenderer =
                new TextureRenderer(models, l10n, bundle.getIcons(), multiblockResolver);
        PageRenderer pageRenderer = new PageRenderer(
                models, l10n, textureRenderer, emiIndex, bundle.getRecipeMountIds());
        SiteRenderer siteRenderer = new SiteRenderer(l10n, output.toString(), recipeBookBaseUrl);

        siteRenderer.copyStaticFiles();
        // Runtime assets: icons from export; GLBs + patchouli:image PNGs written during render.
        // EMI bundle is copied separately by scripts/ci.sh build-site (not part of guide-export assets/).
        siteRenderer.copyHandbookIcons(export);

        Book fallback = bundle.getBooks().loadBook(FIELD_GUIDE);
        List<Language> languages = resolveLanguages(bundle);
        for (Language lang : languages) {
            Book book = lang == Language.EN_US
                    ? fallback
                    : bundle.getBooks().loadBook(FIELD_GUIDE, lang, fallback);
            pageRenderer.setBookMacros(book.getMacros());
            prepare(book, l10n, textureRenderer, pageRenderer);
            siteRenderer.generate(book, textureRenderer);
        }

        log.info("Site generation complete: {}", output);
        return 0;
    }

    private Path resolveEmiRoot(Path export) {
        if (emiDir != null && !emiDir.isBlank()) {
            return Paths.get(emiDir).toAbsolutePath().normalize();
        }
        Path sibling = export.getParent().resolve("emi");
        if (Files.isDirectory(sibling)) {
            return sibling;
        }
        return sibling;
    }

    private List<Language> resolveLanguages(ExportBundle bundle) {
        if (locales != null && !locales.isEmpty()) {
            List<Language> selected = new ArrayList<>();
            for (String code : locales) {
                for (Language lang : Language.values()) {
                    if (lang.getKey().equalsIgnoreCase(code.trim())) {
                        selected.add(lang);
                        break;
                    }
                }
            }
            if (!selected.isEmpty()) {
                return selected;
            }
        }
        return new ArrayList<>(bundle.getLangs().getLanguages());
    }

    private void prepare(
            Book book,
            LocalizationManager localizationManager,
            TextureRenderer textureRenderer,
            PageRenderer pageRenderer) {
        localizationManager.switchLanguage(book.getLanguage());
        book.setName(localizationManager.translate(book.getName()));
        book.setLandingText(localizationManager.translate(book.getLandingText()));
        log.info("Rendering lang={} book={}", book.getLanguage(), book.getName());

        for (BookCategory category : book.getCategories()) {
            prepareCategory(category, localizationManager, book.getMacros());
            for (BookEntry entry : category.getEntries()) {
                if (entry.isRendered()) {
                    continue;
                }
                prepareEntry(entry, textureRenderer);
                for (BookPage page : entry.getPages()) {
                    try {
                        pageRenderer.renderPage(entry, page);
                    } catch (InternalException e) {
                        log.error("Failed to render page: {}", page, e);
                    }
                }
                entry.setInnerHtml(String.join("", entry.getBuffer()));
                entry.setRendered(true);
            }
        }
    }

    private void prepareCategory(
            BookCategory category,
            LocalizationManager localizationManager,
            Map<String, String> bookMacros) {
        category.setName(TextFormatter.stripVanillaFormatting(category.getName()));
        List<String> descriptionBuffer = new ArrayList<>();
        TextFormatter.formatText(descriptionBuffer, category.getDescription(), localizationManager, bookMacros);
        category.setDescription(String.join("", descriptionBuffer));
    }

    private void prepareEntry(BookEntry entry, TextureRenderer textureRenderer) {
        entry.setName(TextFormatter.stripVanillaFormatting(entry.getName()));
        try {
            ItemImageResult itemSrc = textureRenderer.getItemImage(entry.getIcon(), false);
            if (itemSrc != null) {
                entry.setIconPath(itemSrc.previewPath());
                entry.setIconName(itemSrc.getName());
                entry.setIconHeaderHtml(IconMarkup.img(itemSrc, "icon-title me-3"));
                entry.setIconCardHtml(IconMarkup.img(itemSrc, "entry-card-icon me-2"));
            }
        } catch (Exception e) {
            log.error("Failed to get item image for entry: {}", entry.getId(), e);
        }
    }
}
