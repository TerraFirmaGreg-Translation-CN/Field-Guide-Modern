package io.github.tfgcn.fieldguide;

public final class Constants {

    private Constants() {}

    public static final String TEMPLATE_DIR = "assets/templates";

    public static final String MC_VERSION = "1.20.1";
    public static final String FORGE_VERSION = "47.4.6";

    public static final String EN_US = "en_us";

    public static final String FIELD_GUIDE = "field_guide";

    public static final String BOOK_PATH = "data/tfc/patchouli_books/%s/book.json";
    public static final String BOOK_CATEGORY_DIR = "assets/tfc/patchouli_books/%s/%s/categories";
    public static final String BOOK_CATEGORY_PATH = "assets/tfc/patchouli_books/%s/%s/categories/%s.json";
    public static final String BOOK_ENTRY_DIR = "assets/tfc/patchouli_books/%s/%s/entries";
    public static final String BOOK_ENTRY_PATH = "assets/tfc/patchouli_books/%s/%s/entries/%s.json";

    public static String getBookPath() {
        return getBookPath(FIELD_GUIDE);
    }

    public static String getBookPath(String bookId) {
        return String.format(BOOK_PATH, bookId);
    }

    public static String getCategoryDir() {
        return getCategoryDir(FIELD_GUIDE, EN_US);
    }

    public static String getCategoryDir(String lang) {
        return getCategoryDir(FIELD_GUIDE, lang);
    }

    public static String getCategoryDir(String bookId, String lang) {
        return String.format(BOOK_CATEGORY_DIR, bookId, lang);
    }

    public static String getCategoryPath(String categoryId) {
        return getCategoryPath(EN_US, categoryId);
    }

    public static String getCategoryPath(String lang, String categoryId) {
        return getCategoryPath(FIELD_GUIDE, lang, categoryId);
    }

    public static String getCategoryPath(String bookId, String lang, String categoryId) {
        return String.format(BOOK_CATEGORY_PATH, bookId, lang, categoryId);
    }

    public static String getEntryDir() {
        return getEntryDir(EN_US);
    }

    public static String getEntryDir(String lang) {
        return getEntryDir(FIELD_GUIDE, lang);
    }

    public static String getEntryDir(String bookId, String lang) {
        return String.format(BOOK_ENTRY_DIR, bookId, lang);
    }

    public static String getEntryPath(String entryId) {
        return getEntryPath(EN_US, entryId);
    }

    public static String getEntryPath(String lang, String entryId) {
        return getEntryPath(FIELD_GUIDE, lang, entryId);
    }

    public static String getEntryPath(String bookId, String lang, String entryId) {
        return String.format(BOOK_ENTRY_PATH, bookId, lang, entryId);
    }
}