package su.terrafirmgreg.fieldguide.localization;

import com.google.gson.reflect.TypeToken;
import su.terrafirmgreg.fieldguide.asset.AssetLoader;
import su.terrafirmgreg.fieldguide.gson.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

@Slf4j
public class LazyLocalizationManager implements LocalizationManager {

    private final AssetLoader assetLoader;

    private final Map<String, Map<String, String>> translationCache = new TreeMap<>();
    private final Set<String> loadedNamespaces = new TreeSet<>();

    private Language currentLanguage = Language.EN_US;
    private Map<String, String> currentTranslations = new TreeMap<>();
    private final Map<String, String> fallbackTranslations = new TreeMap<>();

    private final Set<String> essentialNamespaces;
    private final Set<String> excludedNamespaces;

    private final Map<String, String> keybindings = new TreeMap<>();

    private final Set<String> missingKeys = new TreeSet<>();

    private int cacheHits = 0;
    private int cacheMisses = 0;

    public LazyLocalizationManager(AssetLoader assetLoader) {
        this.assetLoader = assetLoader;

        this.essentialNamespaces = Set.of(
                "minecraft", "forge", "tfc", "patchouli", "tfg", "beneath", "firmalife", "gtceu", "ae2"
        );

        this.excludedNamespaces = Set.of(
                "cloth_config", "config", "library", "api", "util", "lib",
                "jei", "rei", "emi", "jade", "appleskin", "journeymap", "xaeros_minimap",
                "xaeros_world_map", "worldedit", "litematica", "minihud", "tweakeroo",
                "optifine", "sodium", "iris", "rubidium", "oculus", "magnum", "embeddium",
                "google", "gson", "org_jetbrains", "com_google", "org_slf4j", "org_apache",
                "mixin", "spongepowered", "org_objectweb", "net_kyori", "com_mojang");
        loadEssentialNamespaces();
    }

    private void loadEssentialNamespaces() {
        for (String namespace : essentialNamespaces) {
            loadNamespaceTranslations(namespace, "en_us", fallbackTranslations);
            loadedNamespaces.add(namespace);
        }

        log.info("Pre-loaded essential namespaces: {}", essentialNamespaces);
    }

    private void loadStatic(String lang, Map<String, String> target) {
        try {
            File langFile = new File("assets/lang/%s.json".formatted(lang));
            if (langFile.exists()) {
                Type mapType = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> data = JsonUtils.readFile(langFile, mapType);

                for (Map.Entry<String, String> entry : data.entrySet()) {
                    target.put("field_guide." + entry.getKey(), entry.getValue());
                }
            }
        } catch (IOException e) {
            log.warn("Load local failed: {}", lang, e);
        }
    }

    @Override
    public void switchLanguage(Language lang) {
        String languageCode = lang.getKey();
        this.currentLanguage = lang;
        this.currentTranslations.clear();
        this.currentTranslations = new TreeMap<>();

        log.info("Loading translations for {} namespaces in {}", loadedNamespaces.size(), languageCode);

        for (String namespace : loadedNamespaces) {
            loadNamespaceTranslations(namespace, languageCode, currentTranslations);
            loadedNamespaces.add(namespace);
        }
        loadStatic(languageCode, currentTranslations);

        // 如果当前语言不是英语，确保回退翻译存在
        if (!"en_us".equals(languageCode)) {
            ensureFallbackTranslations(loadedNamespaces);
        }

        keybindings.clear();
        for (String key : I18n.KEYS) {
            String bindingKey = key.substring("field_guide.".length());
            keybindings.put(bindingKey, translate(key));
        }

        log.info("Loaded {} translations for {}", currentTranslations.size(), languageCode);
    }

    private void ensureFallbackTranslations(Set<String> namespaces) {
        for (String namespace : namespaces) {
            if (!loadedNamespaces.contains(namespace)) {
                loadNamespaceTranslations(namespace, "en_us", fallbackTranslations);
                loadedNamespaces.add(namespace);
            }
        }

        loadStatic("en_us", fallbackTranslations);
    }

    private void loadNamespaceTranslations(String namespace, String language, Map<String, String> target) {
        String cacheKey = namespace + ":" + language;

        Map<String, String> translations = translationCache.get(cacheKey);
        if (translations == null) {
            cacheMisses++;
            translations = assetLoader.loadLang(namespace, language);
            translationCache.put(cacheKey, translations);

            if (translations.isEmpty()) {
                log.debug("No translations found for {}:{}", namespace, language);
            }
        } else {
            cacheHits++;
        }

        target.putAll(translations);
    }

    @Override
    public Language getCurrentLanguage() {
        return this.currentLanguage;
    }

    @Override
    public String translate(String... keys) {
        for (String key : keys) {
            if (currentTranslations.containsKey(key)) {
                return currentTranslations.get(key);
            }

            // fallback to en_us
            if (fallbackTranslations.containsKey(key)) {
                return fallbackTranslations.get(key);
            }
        }

        if (!missingKeys.contains(keys[0])) {
            missingKeys.add(keys[0]);
            log.info("Missing translation for: {}", Arrays.toString(keys));
        }
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
        if (excludedNamespaces.contains(namespace)) {
            return;
        }
        if (!loadedNamespaces.contains(namespace)) {
            log.info("Lazy loading namespace: {}", namespace);
            loadNamespaceTranslations(namespace, currentLanguage.getKey(), currentTranslations);
            loadNamespaceTranslations(namespace, "en_us", fallbackTranslations);
            loadedNamespaces.add(namespace);
        }
    }
}
