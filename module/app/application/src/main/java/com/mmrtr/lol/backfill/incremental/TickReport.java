package com.mmrtr.lol.backfill.incremental;

import com.mmrtr.lol.backfill.BackfillFactType;

import java.time.Instant;
import java.util.List;

/**
 * 틱 1회 결과 요약.
 */
public record TickReport(boolean skipped, List<FactReport> facts, boolean cacheEvicted, Instant finishedAt) {

    public static TickReport skippedReport() {
        return new TickReport(true, List.of(), false, Instant.now());
    }

    public record FactReport(
            BackfillFactType factType,
            FactOutcome outcome,
            long startId,
            long endId,
            long rowsLoaded,
            String detail
    ) {
    }

    public enum FactOutcome {
        ADVANCED_EMPTY,
        NO_TARGET_ROWS,
        LOADED,
        ALREADY_COMPLETE,
        HELD,
        FAILED
    }
}
