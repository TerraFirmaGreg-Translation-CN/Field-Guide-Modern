package team.terrafirmgreg.fieldguide.export;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import team.terrafirmgreg.fieldguide.localization.Language;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Locales present in {@code lang/*.json} under guide-export.
 */
@Slf4j
@Getter
public class LangCatalog {

    private final Path exportRoot;
    private final Set<Language> languages;

    private LangCatalog(Path exportRoot, Set<Language> languages) {
        this.exportRoot = exportRoot;
        this.languages = languages;
    }

    public static LangCatalog load(Path exportRoot) {
        Path langDir = exportRoot.resolve("lang");
        if (!Files.isDirectory(langDir)) {
            return new LangCatalog(exportRoot, Set.of());
        }
        EnumSet<Language> found = EnumSet.noneOf(Language.class);
        List<String> unknown = new ArrayList<>();
        try (var stream = Files.list(langDir)) {
            for (Path file : stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json")).sorted().toList()) {
                String code = file.getFileName().toString().replace(".json", "");
                Language lang = languageForKey(code);
                if (lang != null) {
                    found.add(lang);
                } else {
                    unknown.add(code);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list lang files under " + langDir, e);
        }
        for (String code : unknown) {
            log.warn("Export locale {} has no Language enum entry; skipping", code);
        }
        return new LangCatalog(exportRoot, Collections.unmodifiableSet(found));
    }

    public Path langFile(Language language) {
        return exportRoot.resolve("lang").resolve(language.getKey() + ".json");
    }

    private static Language languageForKey(String code) {
        for (Language lang : Language.values()) {
            if (lang.getKey().equals(code)) {
                return lang;
            }
        }
        return null;
    }
}
