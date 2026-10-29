package com.mmrtr.lol.backfill.incremental;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * 통계 export 파이프라인 설정. 활성화(@EnableConfigurationProperties)는 스케줄러 구성에서 한다.
 *
 * @param minObserveAge 범위 상한(observed_until)을 관측한 뒤 export 까지 기다릴 최소 시간. 그 아래 ID 의 트랜잭션이
 *                      커밋될 시간을 보장한다 (설계 5.2). 기본 15분
 */
@ConfigurationProperties("lol.stats-export")
public record StatsExportProperties(
        Scheduler scheduler,
        String cron,
        int patchCount,
        List<Integer> queueIds,
        long seedId,
        Chunk chunk,
        String bqLocation,
        @DefaultValue("15m") Duration minObserveAge
) {

    public record Scheduler(boolean enabled) {
    }

    public record Chunk(int size, int parallelism) {
    }
}
