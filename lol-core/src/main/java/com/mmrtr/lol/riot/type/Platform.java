package com.mmrtr.lol.riot.type;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public enum Platform {

    BR("BR1", "pt_BR", "AMERICAS"),
    EUN("EUN1", "en_GB", "EUROPE"),
    EUW("EUW1", "en_GB", "EUROPE"),
    JP("JP1", "ja_JP", "ASIA"),
    KR("KR", "ko_KR", "ASIA"),
    LAN("LA1", "es_MX", "AMERICAS"),
    LAS("LA2", "es_AR", "AMERICAS"),
    NA("NA1", "en_US", "AMERICAS"),
    OC("OC1", "en_AU", "SEA"),
    RU("RU", "ru_RU", "EUROPE"),
    TH("TR1", "tr_TR", "EUROPE"),
    pH("PH2", "en_PH", "SEA"),
    SG("SG2", "en_SG", "SEA"),
    TR("TH2", "th_TH", "SEA"),
    TW("TW2", "zh_TW", "SEA"),
    VN("VN2", "vn_VN", "SEA"),
    ;

    private static final Map<String, Platform> PLATFORM_NAME = new HashMap<>();
    static {
        for(Platform p : values()) {
            PLATFORM_NAME.put(p.name(), p);
        }
    }

    final String region;
    final String language;
    final String platform;

    Platform(String region, String language, String platform) {
        this.region = region;
        this. language = language;
        this.platform = platform;
    }

    public static Platform valueOfName(String name) {
        return PLATFORM_NAME.get(name.toUpperCase());
    }

    public static String getValueOfName(String name) {

        if(PLATFORM_NAME.containsKey(name.toUpperCase())) {
            return PLATFORM_NAME.get(name.toUpperCase()).name();
        }

        return null;
    }

}
