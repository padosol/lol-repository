package com.mmrtr.lol.backfill.incremental;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * pod 종료 등으로 STARTED 로 남은 lolStatsExportJob 실행을 FAILED 로 마감해 재시작 가능하게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleExecutionCleaner {

    private static final String DESCRIPTION = "stale execution recovered by stats export";

    private final JobExplorer jobExplorer;
    private final JobRepository jobRepository;

    public int markStaleExecutionsFailed() {
        int count = 0;
        for (JobExecution execution : jobExplorer.findRunningJobExecutions(StatsExportJobConfig.JOB_NAME)) {
            LocalDateTime now = LocalDateTime.now();
            for (StepExecution step : execution.getStepExecutions()) {
                if (step.getStatus() == BatchStatus.STARTED || step.getStatus() == BatchStatus.STARTING) {
                    step.setStatus(BatchStatus.FAILED);
                    step.setExitStatus(ExitStatus.FAILED.addExitDescription(DESCRIPTION));
                    step.setEndTime(now);
                    step.setLastUpdated(now);
                    jobRepository.update(step);
                }
            }
            execution.setStatus(BatchStatus.FAILED);
            execution.setExitStatus(ExitStatus.FAILED.addExitDescription(DESCRIPTION));
            execution.setEndTime(now);
            execution.setLastUpdated(now);
            jobRepository.update(execution);
            log.warn("고아 stats export 실행을 FAILED 처리: executionId={}", execution.getId());
            count++;
        }
        return count;
    }
}
