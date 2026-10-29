package com.mmrtr.lol.backfill.incremental;

import com.mmrtr.lol.backfill.BackfillFactType;
import org.springframework.lang.Nullable;

/**
 * fact 별 증분 적재 워터마크. [.., exportedUntil) 는 적재 완료, [exportedUntil, observedUntil) 가 이번 틱 범위.
 */
public record Watermark(BackfillFactType factType, long exportedUntil, long observedUntil, @Nullable String rangePatches) {
}
