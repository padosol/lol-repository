package com.mmrtr.lol.backfill;

import org.springframework.lang.Nullable;

public enum ExportMode {
    BACKFILL,
    INCREMENTAL;

    public static ExportMode fromParam(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return BACKFILL;
        }
        return "incremental".equalsIgnoreCase(value.trim()) ? INCREMENTAL : BACKFILL;
    }
}
