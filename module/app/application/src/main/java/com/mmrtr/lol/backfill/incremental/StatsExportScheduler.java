package com.mmrtr.lol.backfill.incremental;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 챔피언 통계 증분 적재 틱을 주기적으로 실행한다. enabled=true 일 때만 등록된다.
 */
@Component
@ConditionalOnProperty(prefix = "lol.stats-export.scheduler", name = "enabled", havingValue = "true")
public class StatsExportScheduler {

    private final StatsExportService service;

    public StatsExportScheduler(StatsExportService service) {
        this.service = service;
    }

    @Scheduled(cron = "${lol.stats-export.cron:0 20 */2 * * *}", zone = "Asia/Seoul")
    void run() {
        service.runTick();
    }
}
