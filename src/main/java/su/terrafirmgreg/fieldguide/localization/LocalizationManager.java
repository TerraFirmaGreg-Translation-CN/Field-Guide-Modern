package su.terrafirmgreg.fieldguide.localization;

import java.util.Map;

public interface LocalizationManager {

    void switchLanguage(Language lang);

    Language getCurrentLanguage();

    String translate(String ... keys);

    String translateWithArgs(String key, Object ... args);

    Map<String, String> getKeybindings();

    void lazyLoadNamespace(String namespace);
}
