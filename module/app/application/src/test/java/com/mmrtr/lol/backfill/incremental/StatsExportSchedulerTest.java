package com.mmrtr.lol.backfill.incremental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StatsExportSchedulerTest {

    @Test
    @DisplayName("스케줄 실행은 서비스 틱 하나를 부른다")
    void run_delegatesToService() {
        // given
        StatsExportService service = mock(StatsExportService.class);
        StatsExportScheduler scheduler = new StatsExportScheduler(service);

        // when
        scheduler.run();

        // then
        verify(service).runTick();
    }

    @Test
    @DisplayName("enabled 가 true 일 때만 스케줄러 빈이 생긴다")
    void scheduler_conditionalOnEnabled() {
        // given
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(StatsExportScheduler.class)
                .withBean(StatsExportService.class, () -> mock(StatsExportService.class));

        // when / then
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(StatsExportScheduler.class));
        runner.withPropertyValues("lol.stats-export.scheduler.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(StatsExportScheduler.class));
        runner.withPropertyValues("lol.stats-export.scheduler.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(StatsExportScheduler.class));
    }
}
