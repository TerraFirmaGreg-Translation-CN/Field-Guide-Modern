package su.terrafirmgreg.fieldguide.localization;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum Language {
    EN_US("en_us", "English"),
    JA_JP("ja_jp", "日本語"),
    KO_KR("ko_kr", "한국어"),
    PT_BR("pt_br", "Portuguese (Brazil)"),
    RU_RU("ru_ru", "Русский"),
    UK_UA("uk_ua", "Українська мова"),
    ZH_CN("zh_cn", "简体中文(中国大陆)"),
    ZH_HK("zh_hk", "繁體中文（港澳)"),
    ZH_TW("zh_tw", "繁體中文(台灣)"),
    ;
    private final String key;
    private final String value;

    Language(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public static List<Language> asList() {
        return Arrays.asList(values());
    }

    @Override
    public String toString() {
        return key;
    }
}
