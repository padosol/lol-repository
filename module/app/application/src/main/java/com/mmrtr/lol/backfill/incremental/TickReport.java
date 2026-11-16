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
        /** 범위 상한을 관측한 지 min-observe-age 가 안 됐다 — 워터마크·보류 카운터를 건드리지 않고 다음 틱에 다시 본다. */
        DEFERRED,
        ADVANCED_EMPTY,
        NO_TARGET_ROWS,
        LOADED,
        ALREADY_COMPLETE,
        HELD,
        FAILED
    }
}
