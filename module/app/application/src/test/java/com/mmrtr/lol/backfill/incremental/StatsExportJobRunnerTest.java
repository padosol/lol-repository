package com.mmrtr.lol.backfill.incremental;

import com.mmrtr.lol.backfill.BackfillFactType;
import com.mmrtr.lol.backfill.incremental.JobRunOutcome.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsExportJobRunnerTest {

    @Mock
    private JobLauncher launcher;
    @Mock
    private Job job;

    private final StatsExportRequest request =
            new StatsExportRequest(BackfillFactType.MATCH, 100, 200, 16, List.of("16.21"), List.of(420));

    private StatsExportJobRunner runner() {
        return new StatsExportJobRunner(launcher, job);
    }

    @DisplayName("COMPLETED 면 LOADED 와 적재 행 수를 돌려주고, 파라미터는 범위로 결정된다")
    @Test
    void run_completed_returnsLoadedWithRows() throws Exception {
        // given
        JobExecution execution = new JobExecution(1L);
        execution.setStatus(BatchStatus.COMPLETED);
        execution.getExecutionContext().putLong(BqLoadTasklet.CTX_ROWS_LOADED, 42L);
        when(launcher.run(any(Job.class), any(JobParameters.class))).thenReturn(execution);

        // when
        JobRunOutcome outcome = runner().run(request);

        // then
        ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(launcher).run(any(Job.class), captor.capture());
        JobParameters params = captor.getValue();
        assertThat(outcome).isEqualTo(new JobRunOutcome(Status.LOADED, 42, "inc-match-100-200"));
        assertThat(params.getString("runId")).isEqualTo("inc-match-100-200");
        assertThat(params.getString("patches")).isEqualTo("16.21");
        assertThat(params.getString("mode")).isEqualTo("incremental");
        assertThat(params.getLong("season")).isEqualTo(16L);
        assertThat(params.getString("queueIds")).isEqualTo("420");
        assertThat(params.getString("factType")).isEqualTo("match");
        assertThat(params.getLong("startId")).isEqualTo(100L);
        assertThat(params.getLong("endId")).isEqualTo(200L);
    }

    @DisplayName("이미 완료된 인스턴스면 ALREADY_COMPLETE")
    @Test
    void run_alreadyComplete_returnsAlreadyComplete() throws Exception {
        // given
        when(launcher.run(any(Job.class), any(JobParameters.class)))
                .thenThrow(new JobInstanceAlreadyCompleteException("done"));

        // when
        JobRunOutcome outcome = runner().run(request);

        // then
        assertThat(outcome).isEqualTo(new JobRunOutcome(Status.ALREADY_COMPLETE, 0, "inc-match-100-200"));
    }

    @DisplayName("실행이 FAILED 면 FAILED")
    @Test
    void run_failedExecution_returnsFailed() throws Exception {
        // given
        JobExecution execution = new JobExecution(1L);
        execution.setStatus(BatchStatus.FAILED);
        when(launcher.run(any(Job.class), any(JobParameters.class))).thenReturn(execution);

        // when
        JobRunOutcome outcome = runner().run(request);

        // then
        assertThat(outcome.status()).isEqualTo(Status.FAILED);
    }

    @DisplayName("launcher 가 이미 실행 중 예외를 던지면 FAILED")
    @Test
    void run_alreadyRunning_returnsFailed() throws Exception {
        // given
        when(launcher.run(any(Job.class), any(JobParameters.class)))
                .thenThrow(new JobExecutionAlreadyRunningException("running"));

        // when
        JobRunOutcome outcome = runner().run(request);

        // then
        assertThat(outcome).isEqualTo(new JobRunOutcome(Status.FAILED, 0, "inc-match-100-200"));
    }
}
