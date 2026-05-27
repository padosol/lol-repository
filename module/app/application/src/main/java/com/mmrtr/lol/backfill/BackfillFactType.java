package com.mmrtr.lol.backfill;

public enum BackfillFactType {

    MATCH("match",
            "sql/match-aggregation.sql",
            "match/backfill"),
    MATCH_BAN("match-ban",
            "sql/match-ban-aggregation.sql",
            "match_ban/backfill"),
    MATCH_PARTICIPANT_BUILD("match-participant-build",
            "sql/match-participant-build-aggregation.sql",
            "match_participant_build/backfill");

    private final String code;
    private final String sqlResourcePath;
    private final String gcsPrefix;

    BackfillFactType(String code, String sqlResourcePath, String gcsPrefix) {
        this.code = code;
        this.sqlResourcePath = sqlResourcePath;
        this.gcsPrefix = gcsPrefix;
    }

    public String code() {
        return code;
    }

    public String sqlResourcePath() {
        return sqlResourcePath;
    }

    public String gcsPrefix() {
        return gcsPrefix;
    }

    public static BackfillFactType fromCode(String code) {
        for (BackfillFactType t : values()) {
            if (t.code.equalsIgnoreCase(code)) {
                return t;
            }
        }
        throw new IllegalArgumentException(
                "Unknown backfill fact type: " + code
                        + " (allowed: match, match-ban, match-participant-build)");
    }
}
