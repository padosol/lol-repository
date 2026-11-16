package com.mmrtr.lol.backfill.incremental;

/**
 * 시즌 기준 데이터(item_meta) 분류별 활성 행 수.
 */
public record ReferenceDataStatus(int legendary, int boots, int startingExclusion) {

    public boolean complete() {
        return legendary > 0 && boots > 0 && startingExclusion > 0;
    }
}
