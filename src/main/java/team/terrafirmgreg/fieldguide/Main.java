package team.terrafirmgreg.fieldguide;

import freemarker.template.TemplateException;
import team.terrafirmgreg.fieldguide.asset.AssetLoader;
import team.terrafirmgreg.fieldguide.asset.ItemImageResult;
import team.terrafirmgreg.fieldguide.data.patchouli.Book;
import team.terrafirmgreg.fieldguide.data.patchouli.BookCategory;
import team.terrafirmgreg.fieldguide.data.patchouli.BookEntry;
import team.terrafirmgreg.fieldguide.data.patchouli.BookPage;
import team.terrafirmgreg.fieldguide.exception.InternalException;
import team.terrafirmgreg.fieldguide.localization.Language;
import team.terrafirmgreg.fieldguide.localization.LazyLocalizationManager;
import team.terrafirmgreg.fieldguide.localization.LocalizationManager;
import team.terrafirmgreg.fieldguide.render.HtmlRenderer;
import team.terrafirmgreg.fieldguide.render.PageRenderer;
import team.terrafirmgreg.fieldguide.render.TextFormatter;
import team.terrafirmgreg.fieldguide.render.TextureRenderer;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static team.terrafirmgreg.fieldguide.Constants.FIELD_GUIDE;

@Slf4j
public class Main implements Callable<Integer>  {

    @CommandLine.Option(
            names = {"-i", "--tfg-dir"},
            required = true,
            description = {"The dir of TerraFirmaGreg modpack.",
                    "e.g. \"/Users/yanmaoyuan/games/tfg-0.11.10\""},
            defaultValue = "./Modpack-Modern",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS
    )
    protected String inputDir;

    @CommandLine.Option(
            names = {"-o", "--out-dir"},
            description = "The dir of output. e.g. \"./output\"",
            defaultValue = "./output",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS
    )
    protected String outputDir;

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new Main());
        System.exit(cmd.execute(args));
    }

    @Override
    public Integer call() throws Exception {
        log.info("Start parsing fallback..., tfg: {}, out: {}", inputDir, outputDir);

        // The TerraFirmaGreg modpack directory
        String modpackPath = inputDir.replace("\\", "/");
        String outputRootPath = outputDir.replace("\\", "/");

        AssetLoader assetLoader = new AssetLoader(Paths.get(modpackPath), Paths.get(outputRootPath));

        LocalizationManager localizationManager = new LazyLocalizationManager(assetLoader);

        TextureRenderer textureRenderer = new TextureRenderer(assetLoader, localizationManager);

        PageRenderer pageRenderer = new PageRenderer(assetLoader, localizationManager, textureRenderer);

        HtmlRenderer htmlRenderer = new HtmlRenderer(localizationManager, outputDir);
        // Load en_us book as a fallback
        Book fallback = assetLoader.loadBook(FIELD_GUIDE);
        prepare(fallback, localizationManager, textureRenderer, pageRenderer);

        for (Language lang : Language.values()) {
            Book book = assetLoader.loadBook(FIELD_GUIDE, lang, fallback);
            prepare(book, localizationManager, textureRenderer, pageRenderer);
            generateHtml(book, htmlRenderer);
        }

        assetLoader.getAssetStats().print();
        return 0;
    }

    public void prepare(Book book, LocalizationManager localizationManager, TextureRenderer textureRenderer, PageRenderer pageRenderer) {

        // prepare
        localizationManager.switchLanguage(book.getLanguage());
        String name = localizationManager.translate(book.getName());
        book.setName(name);
        String landingText = localizationManager.translate(book.getLandingText());
        book.setLandingText(landingText);
        log.info("Start parsing lang: {}, {} {} - {}", book.getLanguage(), name, book.getSubtitle(), landingText);

        // render categories
        for (BookCategory category : book.getCategories()) {
            prepareCategory(category, localizationManager);

            for (BookEntry entry : category.getEntries()) {

                if (entry.isRendered()) {
                    continue;
                }
                prepareEntry(entry, textureRenderer);

                for (BookPage page : entry.getPages()) {
                    try {
                        pageRenderer.renderPage(entry, page);
                    } catch (InternalException e) {
                        log.error("Failed to parse page: {}", page, e);
                    }
                }

                // render inner html
                entry.setInnerHtml(String.join("", entry.getBuffer()));
                entry.setRendered(true);
            }
        }
    }

    public void prepareCategory(BookCategory category, LocalizationManager localizationManager) {
        // remove "§."
        category.setName(TextFormatter.stripVanillaFormatting(category.getName()));

        // format description text
        List<String> descriptionBuffer = new ArrayList<>();
        TextFormatter.formatText(descriptionBuffer, category.getDescription(), localizationManager.getKeybindings());
        category.setDescription(String.join("", descriptionBuffer));
    }

    public void prepareEntry(BookEntry entry, TextureRenderer textureRenderer) {
        entry.setName(TextFormatter.stripVanillaFormatting(entry.getName()));
        try {
            ItemImageResult itemSrc = textureRenderer.getItemImage(entry.getIcon(), false);
            if (itemSrc != null) {
                entry.setIconPath(itemSrc.getPath());
                entry.setIconName(itemSrc.getName());
            } else {
                log.error("Item image is null for entry: {}", entry.getId());
            }
        } catch (Exception e) {
            log.error("Failed to get item image for entry: {}", entry.getId());
        }
    }

    public void generateHtml(Book book, HtmlRenderer htmlRenderer) throws IOException, TemplateException {

        htmlRenderer.copyStaticFiles();

        // Home page
        htmlRenderer.buildHomePage(book.getCategories());

        // Search page
        htmlRenderer.buildSearchPage(book.getCategories());

        // search data
        List<Map<String, String>> searchData = new ArrayList<>();
        for (BookEntry entry : book.getEntries()) {
            searchData.addAll(entry.getSearchTree());
        }
        htmlRenderer.saveSearchData(searchData);

        // Category pages
        for (BookCategory category : book.getCategories()) {
            htmlRenderer.buildCategoryPage(category, book.getCategories());
        }

        System.out.println("Static site generated successfully!");
    }
}