package com.mmrtr.lol.backfill.incremental;

import com.mmrtr.lol.backfill.incremental.JobRunOutcome.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * lolStatsExportJob 을 동기로 실행하고 결과를 요약한다.
 */
@Slf4j
@Component
public class StatsExportJobRunner {

    private final JobLauncher launcher;
    private final Job job;

    public StatsExportJobRunner(
            @Qualifier("statsExportJobLauncher") JobLauncher launcher,
            @Qualifier("lolStatsExportJob") Job job
    ) {
        this.launcher = launcher;
        this.job = job;
    }

    public JobRunOutcome run(StatsExportRequest request) {
        String runId = request.runId();
        try {
            JobExecution execution = launcher.run(job, request.toJobParameters());
            if (execution.getStatus() == BatchStatus.COMPLETED) {
                long rows = execution.getExecutionContext().getLong(BqLoadTasklet.CTX_ROWS_LOADED, 0L);
                return new JobRunOutcome(Status.LOADED, rows, runId);
            }
            log.warn("stats export 실행이 완료되지 않았다: runId={}, status={}", runId, execution.getStatus());
            return new JobRunOutcome(Status.FAILED, 0, runId);
        } catch (JobInstanceAlreadyCompleteException e) {
            log.info("이미 완료된 stats export 범위: runId={}", runId);
            return new JobRunOutcome(Status.ALREADY_COMPLETE, 0, runId);
        } catch (Exception e) {
            log.error("stats export 실행 실패: runId={}", runId, e);
            return new JobRunOutcome(Status.FAILED, 0, runId);
        }
    }
}
