package com.mmrtr.lol.backfill.incremental;

import com.mmrtr.lol.backfill.incremental.JobRunOutcome.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.explore.JobExplorer;
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
    private final JobExplorer jobExplorer;

    public StatsExportJobRunner(
            @Qualifier("statsExportJobLauncher") JobLauncher launcher,
            @Qualifier("lolStatsExportJob") Job job,
            JobExplorer jobExplorer
    ) {
        this.launcher = launcher;
        this.job = job;
        this.jobExplorer = jobExplorer;
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
            return alreadyCompleteOutcome(request);
        } catch (Exception e) {
            log.error("stats export 실행 실패: runId={}", runId, e);
            return new JobRunOutcome(Status.FAILED, 0, runId);
        }
    }

    /**
     * Spring Batch 는 COMPLETED 뿐 아니라 ABANDONED 실행이 있어도 이 예외를 던진다. 실제로 끝까지 돈(COMPLETED) 실행이
     * 있을 때만 적재 완료로 보고, 아니면 적재 없이 워터마크가 지나가지 않도록 실패로 둔다.
     */
    private JobRunOutcome alreadyCompleteOutcome(StatsExportRequest request) {
        String runId = request.runId();
        try {
            JobInstance instance = jobExplorer.getJobInstance(StatsExportJobConfig.JOB_NAME, request.toJobParameters());
            boolean completed = instance != null && jobExplorer.getJobExecutions(instance).stream()
                    .anyMatch(execution -> execution.getStatus() == BatchStatus.COMPLETED);
            if (completed) {
                log.info("이미 완료된 stats export 범위: runId={}", runId);
                return new JobRunOutcome(Status.ALREADY_COMPLETE, 0, runId);
            }
        } catch (RuntimeException e) {
            log.error("stats export 인스턴스 조회 실패: runId={}", runId, e);
            return new JobRunOutcome(Status.FAILED, 0, runId);
        }
        log.error("stats export 인스턴스가 COMPLETED 실행 없이 재시작 불가(ABANDONED 등): runId={}, exportEpoch={}."
                        + " 같은 범위를 다시 export 하려면 export_epoch 를 올린다:"
                        + " UPDATE stats_export_watermark SET export_epoch = export_epoch + 1 WHERE fact_type = '{}'",
                runId, request.exportEpoch(), request.factType().code());
        return new JobRunOutcome(Status.FAILED, 0, runId);
    }
}
