package com.mmrtr.lol.tools.riotmock.world;

/** 매치 값을 고를 때 쓰는 실제 게임 ID 의 고정 목록. */
final class LabCatalog {

    static final int[] CHAMPION_IDS = {1, 22, 24, 29, 51, 61, 64, 86, 99, 103, 117, 119, 157, 202, 222, 236, 238, 245, 266, 412};
    static final String[] CHAMPION_NAMES = {"Annie", "Ashe", "Jax", "Twitch", "Caitlyn", "Orianna", "LeeSin", "Garen", "Lux", "Ahri",
        "Lulu", "Draven", "Yasuo", "Jhin", "Jinx", "Lucian", "Zed", "Ekko", "Aatrox", "Thresh"};
    static final int[] ITEM_IDS = {3003, 3006, 3020, 3031, 3036, 3046, 3071, 3072, 3087, 3089, 3094, 3095, 3107, 3135, 3153, 3157, 3165, 6672, 6673};
    static final int TRINKET_ID = 3340;
    static final int[] SUMMONER_SPELL_IDS = {4, 14, 7, 3, 12, 11, 6, 21};
    static final int FLASH = 4;
    static final int SMITE = 11;

    static final int[] STYLE_IDS = {8000, 8100, 8200, 8300, 8400};
    /** 스타일별 룬 4개(주 룬 4개를 그대로, 보조 룬은 앞의 2개). */
    static final int[][] STYLE_PERKS = {
        {8005, 9111, 9104, 8014},
        {8112, 8126, 8139, 8106},
        {8214, 8226, 8210, 8237},
        {8351, 8306, 8345, 8347},
        {8437, 8446, 8429, 8451},
    };
    static final int STAT_DEFENSE = 5011;
    static final int STAT_FLEX = 5008;
    static final int STAT_OFFENSE = 5005;

    static final String[] POSITIONS = {"TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY"};
    static final String[] LANES = {"TOP", "JUNGLE", "MIDDLE", "BOTTOM", "BOTTOM"};
    static final String[] ROLES = {"SOLO", "NONE", "SOLO", "CARRY", "SUPPORT"};

    private LabCatalog() {
    }
}
