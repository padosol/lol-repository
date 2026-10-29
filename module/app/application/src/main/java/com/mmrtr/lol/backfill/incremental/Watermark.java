package com.mmrtr.lol.backfill.incremental;

import com.mmrtr.lol.backfill.BackfillFactType;
import org.springframework.lang.Nullable;

import java.time.Instant;

/**
 * fact 별 증분 적재 워터마크. [.., exportedUntil) 는 적재 완료, [exportedUntil, observedUntil) 가 이번 틱 범위.
 *
 * @param observedAt  observedUntil 을 기록한 시각. 이 시각부터 min-observe-age 가 지나야 범위를 export 한다 (설계 5.2)
 * @param exportEpoch 같은 범위를 새 JobInstance 로 다시 export 하게 하는 운영 카운터 (설계 11, 막힌 범위 복구)
 */
public record Watermark(
        BackfillFactType factType,
        long exportedUntil,
        long observedUntil,
        @Nullable String rangePatches,
        Instant observedAt,
        int exportEpoch
) {
}
