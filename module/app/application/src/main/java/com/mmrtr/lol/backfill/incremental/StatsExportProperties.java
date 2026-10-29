package com.mmrtr.lol.backfill.incremental;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 통계 export 파이프라인 설정. 활성화(@EnableConfigurationProperties)는 스케줄러 구성에서 한다.
 */
@ConfigurationProperties("lol.stats-export")
public record StatsExportProperties(
        Scheduler scheduler,
        String cron,
        int patchCount,
        List<Integer> queueIds,
        long seedId,
        Chunk chunk,
        String bqLocation
) {

    public record Scheduler(boolean enabled) {
    }

    public record Chunk(int size, int parallelism) {
    }
}
