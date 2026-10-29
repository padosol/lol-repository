package com.mmrtr.lol.backfill.incremental;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 워터마크 범위가 내보낼 대상 패치를 결정한다. 범위에 패치 테이블이 모르는 새 패치/시즌이 있으면
 * 새 패치 행이 영구히 건너뛰어지지 않도록 보류한다.
 */
public final class TargetPatchPolicy {

    private TargetPatchPolicy() {
    }

    /**
     * @param tableLatestDesc 패치 테이블의 최신 시즌 최근 N개 패치 (최신순)
     * @param rangeMax        범위 안 매치의 최대 패치 (행이 없으면 empty)
     */
    public static TargetDecision decide(List<PatchVersion> tableLatestDesc, Optional<PatchVersion> rangeMax) {
        if (tableLatestDesc.isEmpty()) {
            return new Hold("패치 테이블이 비어 있습니다");
        }
        PatchVersion tableLatest = tableLatestDesc.get(0);
        if (rangeMax.isPresent() && rangeMax.get().compareTo(tableLatest) > 0) {
            return new Hold("패치 테이블보다 새 패치 행이 존재합니다: range=" + rangeMax.get().value()
                    + ", tableLatest=" + tableLatest.value());
        }
        PatchVersion tableOldest = Collections.min(tableLatestDesc);
        if (rangeMax.isEmpty() || rangeMax.get().compareTo(tableOldest) < 0) {
            return new Proceed(List.of());
        }
        return new Proceed(List.copyOf(tableLatestDesc));
    }

    public sealed interface TargetDecision permits Proceed, Hold {
    }

    public record Proceed(List<PatchVersion> patches) implements TargetDecision {
    }

    public record Hold(String reason) implements TargetDecision {
    }
}
