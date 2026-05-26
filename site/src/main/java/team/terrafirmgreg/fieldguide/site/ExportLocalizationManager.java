package team.terrafirmgreg.fieldguide.site;

import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import team.terrafirmgreg.fieldguide.export.LangCatalog;
import team.terrafirmgreg.fieldguide.gson.JsonUtils;
import team.terrafirmgreg.fieldguide.localization.I18n;
import team.terrafirmgreg.fieldguide.localization.Language;
import team.terrafirmgreg.fieldguide.localization.LocalizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loads merged {@code lang/<locale>.json} from guide-export (no mod jar scanning).
 * Also merges site UI strings from {@code assets/lang/<locale>.json} (CLI parity).
 */
@Slf4j
public class ExportLocalizationManager implements LocalizationManager {

    private static final TypeToken<Map<String, String>> LANG_TYPE = new TypeToken<>() {};

    private final LangCatalog langCatalog;
    private final Path siteLangRoot;
    private final Map<String, String> fallbackTranslations = new TreeMap<>();
    private Map<String, String> currentTranslations = new TreeMap<>();
    private Language currentLanguage = Language.EN_US;
    private final Map<String, String> keybindings = new TreeMap<>();

    public ExportLocalizationManager(LangCatalog langCatalog) {
        this(langCatalog, Path.of("assets/lang"));
    }

    public ExportLocalizationManager(LangCatalog langCatalog, Path siteLangRoot) {
        this.langCatalog = langCatalog;
        this.siteLangRoot = siteLangRoot.normalize().toAbsolutePath();
        if (Files.isRegularFile(langCatalog.langFile(Language.EN_US))) {
            fallbackTranslations.putAll(readLangFile(langCatalog.langFile(Language.EN_US)));
        }
        mergeSiteLang(Language.EN_US, fallbackTranslations);
    }

    @Override
    public void switchLanguage(Language lang) {
        this.currentLanguage = lang;
        currentTranslations = new TreeMap<>();
        Path langFile = langCatalog.langFile(lang);
        if (Files.isRegularFile(langFile)) {
            currentTranslations.putAll(readLangFile(langFile));
            log.info("Loaded {} export translations for {}", currentTranslations.size(), lang.getKey());
        } else {
            log.warn("Missing export lang file for {}", lang.getKey());
        }
        mergeSiteLang(lang, currentTranslations);

        keybindings.clear();
        for (String key : I18n.KEYS) {
            String bindingKey = key.substring("field_guide.".length());
            keybindings.put(bindingKey, translate(key));
        }
    }

    private void mergeSiteLang(Language lang, Map<String, String> target) {
        Path langFile = siteLangRoot.resolve(lang.getKey() + ".json");
        if (!Files.isRegularFile(langFile)) {
            return;
        }
        Map<String, String> site = readLangFile(langFile);
        for (Map.Entry<String, String> entry : site.entrySet()) {
            target.put(I18n.key(entry.getKey()), entry.getValue());
        }
    }

    private static Map<String, String> readLangFile(java.nio.file.Path langFile) {
        try {
            String json = Files.readString(langFile);
            Map<String, String> map = JsonUtils.GSON.fromJson(json, LANG_TYPE.getType());
            return map != null ? new TreeMap<>(map) : new TreeMap<>();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + langFile, e);
        }
    }

    @Override
    public Language getCurrentLanguage() {
        return currentLanguage;
    }

    @Override
    public String translate(String... keys) {
        for (String key : keys) {
            if (currentTranslations.containsKey(key)) {
                return currentTranslations.get(key);
            }
            if (fallbackTranslations.containsKey(key)) {
                return fallbackTranslations.get(key);
            }
        }
        log.debug("Missing translation for: {}", Arrays.toString(keys));
        return keys[0];
    }

    @Override
    public String translateWithArgs(String key, Object... args) {
        return String.format(translate(key), args);
    }

    @Override
    public Map<String, String> getKeybindings() {
        return keybindings;
    }

    @Override
    public void lazyLoadNamespace(String namespace) {
        // Export lang files are already merged; nothing to lazy-load per namespace.
    }
}
