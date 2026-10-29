package com.mmrtr.lol.backfill.incremental;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * 통계 export 파이프라인 설정. 활성화(@EnableConfigurationProperties)는 스케줄러 구성에서 한다.
 * 잘못된 값은 기동 시점에 막는다 — 예를 들어 빈 queueIds 는 모든 범위를 "대상 행 없음" 으로 건너뛰게 한다.
 *
 * @param minObserveAge 범위 상한(observed_until)을 관측한 뒤 export 까지 기다릴 최소 시간. 그 아래 ID 의 트랜잭션이
 *                      커밋될 시간을 보장한다 (설계 5.2). 기본 15분
 */
@Validated
@ConfigurationProperties("lol.stats-export")
public record StatsExportProperties(
        Scheduler scheduler,
        String cron,
        @Min(1) int patchCount,
        @NotEmpty List<Integer> queueIds,
        long seedId,
        @NotNull @Valid Chunk chunk,
        String bqLocation,
        @NotNull @DefaultValue("15m") Duration minObserveAge
) {

    public record Scheduler(boolean enabled) {
    }

    /**
     * @param parallelism 청크마다 PG 커넥션을 오래 잡으므로 본업 풀(Hikari 10)을 지키려고 4 이하로 둔다 (설계 11)
     */
    public record Chunk(@Positive int size, @Min(1) @Max(4) int parallelism) {
    }
}
