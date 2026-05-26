package team.terrafirmgreg.fieldguide.export;

import lombok.extern.slf4j.Slf4j;
import team.terrafirmgreg.fieldguide.Constants;
import team.terrafirmgreg.fieldguide.asset.Asset;
import team.terrafirmgreg.fieldguide.data.patchouli.Book;
import team.terrafirmgreg.fieldguide.data.patchouli.BookCategory;
import team.terrafirmgreg.fieldguide.data.patchouli.BookEntry;
import team.terrafirmgreg.fieldguide.exception.AssetNotFoundException;
import team.terrafirmgreg.fieldguide.gson.JsonUtils;
import team.terrafirmgreg.fieldguide.localization.Language;

import java.io.IOException;
import java.util.List;

/**
 * Loads Patchouli book JSON from guide-export {@code assets/} tree.
 */
@Slf4j
public class ExportBookLoader {

    private final ExportModelLoader models;

    public ExportBookLoader(ExportModelLoader models) {
        this.models = models;
    }

    public Book loadBook(String bookId) throws IOException {
        return loadBook(bookId, Language.EN_US, null);
    }

    public Book loadBook(String bookId, Language lang, Book fallback) throws IOException {
        String bookPath = Constants.getBookPath(bookId);
        Asset bookAsset = models.getAsset(bookPath);
        if (bookAsset == null) {
            throw new AssetNotFoundException("Book not found: " + bookPath);
        }

        Book book = JsonUtils.readFile(bookAsset.getInputStream(), Book.class);
        book.setLanguage(lang);
        book.setAssetSource(bookAsset);

        if (fallback == null) {
            loadCategoriesAndEntries(book, bookId, lang.getKey());
        } else {
            mergeLocalized(book, bookId, lang, fallback);
        }

        book.sort();
        return book;
    }

    private void loadCategoriesAndEntries(Book book, String bookId, String lang) throws IOException {
        String categoryDir = Constants.getCategoryDir(bookId, lang);
        for (Asset asset : models.listAssets(categoryDir)) {
            BookCategory category = JsonUtils.readFile(asset.getInputStream(), BookCategory.class);
            category.setAssetSource(categoryDir, asset);
            if (Constants.EXCLUDES_CATEGORIES.contains(category.getId())) {
                continue;
            }
            book.addCategory(category);
        }

        String entryDir = Constants.getEntryDir(bookId, lang);
        for (Asset asset : models.listAssets(entryDir)) {
            BookEntry entry = JsonUtils.readFile(asset.getInputStream(), BookEntry.class);
            entry.setAssetSource(entryDir, asset);
            if (Constants.EXCLUDES_ENTRIES.contains(entry.getId())) {
                continue;
            }
            book.addEntry(entry);
        }
    }

    private void mergeLocalized(Book book, String bookId, Language lang, Book fallback) throws IOException {
        String categoryDir = Constants.getCategoryDir(bookId, lang.getKey());
        String fallbackCategoryDir = Constants.getCategoryDir();
        for (BookCategory category : fallback.getCategories()) {
            String path = Constants.getCategoryPath(lang.getKey(), category.getId());
            Asset asset = models.getAsset(path);
            if (asset != null) {
                BookCategory localized = JsonUtils.readFile(asset.getInputStream(), BookCategory.class);
                localized.setAssetSource(categoryDir, asset);
                book.addCategory(localized);
            } else {
                path = Constants.getCategoryPath(category.getId());
                asset = models.getAsset(path);
                if (asset != null) {
                    BookCategory fb = JsonUtils.readFile(asset.getInputStream(), BookCategory.class);
                    fb.setAssetSource(fallbackCategoryDir, asset);
                    book.addCategory(fb);
                }
            }
        }

        String entryDir = Constants.getEntryDir(lang.getKey());
        String fallbackEntryDir = Constants.getEntryDir();
        for (BookEntry entry : fallback.getEntries()) {
            String path = Constants.getEntryPath(lang.getKey(), entry.getId());
            Asset asset = models.getAsset(path);
            if (asset != null) {
                BookEntry localized = JsonUtils.readFile(asset.getInputStream(), BookEntry.class);
                localized.setAssetSource(entryDir, asset);
                book.addEntry(localized);
            } else {
                path = Constants.getEntryPath(entry.getId());
                asset = models.getAsset(path);
                if (asset != null) {
                    BookEntry fb = JsonUtils.readFile(asset.getInputStream(), BookEntry.class);
                    fb.setAssetSource(fallbackEntryDir, asset);
                    book.addEntry(fb);
                }
            }
        }
    }
}
