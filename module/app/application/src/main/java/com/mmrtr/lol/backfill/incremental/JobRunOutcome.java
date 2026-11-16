package com.mmrtr.lol.backfill.incremental;

/**
 * 증분 export 1회 실행 결과.
 */
public record JobRunOutcome(Status status, long rowsLoaded, String runId) {

    public enum Status {
        LOADED,
        ALREADY_COMPLETE,
        FAILED
    }
}
