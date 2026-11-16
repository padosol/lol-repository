package com.mmrtr.lol.backfill.incremental;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 챔피언 통계 증분 적재 틱을 주기적으로 실행한다. enabled=true 일 때만 등록된다.
 * 틱은 수동 /run 과 같은 트리거 실행기(동시 1개)로 넘겨 공용 스케줄러 스레드를 잡지 않고,
 * 수동 실행이 돌고 있으면 이번 틱은 건너뛴다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "lol.stats-export.scheduler", name = "enabled", havingValue = "true")
public class StatsExportScheduler {

    private final StatsExportService service;
    private final TaskExecutor triggerExecutor;

    public StatsExportScheduler(
            StatsExportService service,
            @Qualifier("statsExportTriggerExecutor") TaskExecutor triggerExecutor
    ) {
        this.service = service;
        this.triggerExecutor = triggerExecutor;
    }

    @Scheduled(cron = "${lol.stats-export.cron:0 20 */2 * * *}", zone = "Asia/Seoul")
    void run() {
        try {
            triggerExecutor.execute(() -> service.runTickQuietly("스케줄"));
        } catch (TaskRejectedException e) {
            log.info("stats export 스케줄 틱 건너뜀: 다른 틱(수동 실행)이 진행 중");
        }
    }
}
