package com.mmrtr.lol.backfill;

public enum BackfillFactType {

    MATCH("match",
            "sql/match-aggregation.sql",
            "match/backfill",
            "match/incremental", "match_fact", "match", "id"),
    MATCH_BAN("match-ban",
            "sql/match-ban-aggregation.sql",
            "match_ban/backfill",
            "match_ban/incremental", "match_ban_fact", "match_ban", "id"),
    MATCH_PARTICIPANT_BUILD("match-participant-build",
            "sql/match-participant-build-aggregation.sql",
            "match_participant_build/backfill",
            "match_participant_build/incremental", "match_participant_fact", "match_participant",
            "match_participant_id");

    private final String code;
    private final String sqlResourcePath;
    private final String gcsPrefix;
    private final String incrementalGcsPrefix;
    private final String bqTable;
    private final String sourceTable;
    private final String sourceIdColumn;

    BackfillFactType(String code, String sqlResourcePath, String gcsPrefix, String incrementalGcsPrefix,
                     String bqTable, String sourceTable, String sourceIdColumn) {
        this.code = code;
        this.sqlResourcePath = sqlResourcePath;
        this.gcsPrefix = gcsPrefix;
        this.incrementalGcsPrefix = incrementalGcsPrefix;
        this.bqTable = bqTable;
        this.sourceTable = sourceTable;
        this.sourceIdColumn = sourceIdColumn;
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

    public String gcsPrefixFor(ExportMode mode) {
        return mode == ExportMode.INCREMENTAL ? incrementalGcsPrefix : gcsPrefix;
    }

    public String bqTable() {
        return bqTable;
    }

    public String sourceTable() {
        return sourceTable;
    }

    public String sourceIdColumn() {
        return sourceIdColumn;
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
