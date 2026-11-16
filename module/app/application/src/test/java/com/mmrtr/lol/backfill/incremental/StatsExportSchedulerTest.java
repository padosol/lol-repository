package com.mmrtr.lol.backfill.incremental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

class StatsExportSchedulerTest {

    @Test
    @DisplayName("스케줄 실행은 틱을 트리거 실행기에 넘기고 스케줄러 스레드에서 직접 돌리지 않는다")
    void run_submitsTickToTriggerExecutor() {
        // given
        StatsExportService service = mock(StatsExportService.class);
        TaskExecutor executor = mock(TaskExecutor.class);
        StatsExportScheduler scheduler = new StatsExportScheduler(service, executor);

        // when
        scheduler.run();

        // then
        ArgumentCaptor<Runnable> submitted = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(submitted.capture());
        verifyNoInteractions(service);
        submitted.getValue().run();
        verify(service).runTickQuietly("스케줄");
    }

    @Test
    @DisplayName("수동 실행이 돌고 있어 실행기가 거절하면 예외 없이 이번 틱을 건너뛴다")
    void run_executorBusy_skipsWithoutThrowing() {
        // given
        StatsExportService service = mock(StatsExportService.class);
        TaskExecutor executor = mock(TaskExecutor.class);
        doThrow(new TaskRejectedException("busy")).when(executor).execute(any(Runnable.class));
        StatsExportScheduler scheduler = new StatsExportScheduler(service, executor);

        // when / then
        assertThatCode(scheduler::run).doesNotThrowAnyException();
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("enabled 가 true 일 때만 스케줄러 빈이 생긴다")
    void scheduler_conditionalOnEnabled() {
        // given
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(StatsExportScheduler.class)
                .withBean(StatsExportService.class, () -> mock(StatsExportService.class))
                .withBean("statsExportTriggerExecutor", TaskExecutor.class, SyncTaskExecutor::new);

        // when / then
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(StatsExportScheduler.class));
        runner.withPropertyValues("lol.stats-export.scheduler.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(StatsExportScheduler.class));
        runner.withPropertyValues("lol.stats-export.scheduler.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(StatsExportScheduler.class));
    }
}
