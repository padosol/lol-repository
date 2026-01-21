package com.mmrtr.lol.common.type;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public enum Platform {

    BR("BR1", "pt_BR", "https://americas.api.riotgames.com", "https://br.api.riotgames.com"),
    EUN("EUN1", "en_GB", "https://europe.api.riotgames.com", "https://eun1.api.riotgames.com"),
    EUW("EUW1", "en_GB", "https://europe.api.riotgames.com", "https://euw1.api.riotgames.com"),
    JP("JP1", "ja_JP", "https://asia.api.riotgames.com", "https://jp1.api.riotgames.com"),
    KR("KR", "ko_KR", "https://asia.api.riotgames.com", "https://kr.api.riotgames.com"),
    LAN("LA1", "es_MX", "https://americas.api.riotgames.com", "https://la1.api.riotgames.com"),
    LAS("LA2", "es_AR", "https://americas.api.riotgames.com", "https://la2.api.riotgames.com"),
    NA("NA1", "en_US", "https://americas.api.riotgames.com", "https://na1.api.riotgames.com"),
    OC("OC1", "en_AU", "https://sea.api.riotgames.com", "https://oc1.api.riotgames.com"),
    RU("RU", "ru_RU", "https://europe.api.riotgames.com", "https://ru.api.riotgames.com"),
    TR("TR1", "tr_TR", "https://europe.api.riotgames.com", "https://tr1.api.riotgames.com"),
    PH("PH2", "en_PH", "https://sea.api.riotgames.com", "https://ph2.api.riotgames.com"),
    SG("SG2", "en_SG", "https://sea.api.riotgames.com", "https://sg2.api.riotgames.com"),
    TH("TH2", "th_TH", "https://sea.api.riotgames.com", "https://th2.api.riotgames.com"),
    TW("TW2", "zh_TW", "https://sea.api.riotgames.com", "https://tw2.api.riotgames.com"),
    VN("VN2", "vn_VN", "https://sea.api.riotgames.com", "https://vn2.api.riotgames.com"),
    ;

    private static final Map<String, Platform> PLATFORM_NAME = new HashMap<>();
    static {
        for (Platform p : values()) {
            PLATFORM_NAME.put(p.name(), p);
        }
    }

    private final String platformId;
    private final String language;
    private final String regionalHost;
    private final String platformHost;

    Platform(String platformId, String language, String regionalHost, String platformHost) {
        this.platformId = platformId;
        this.language = language;
        this.regionalHost = regionalHost;
        this.platformHost = platformHost;
    }

    public static Platform valueOfName(String name) {
        return PLATFORM_NAME.get(name.toUpperCase());
    }
}
