package com.mmrtr.lol.backfill.incremental;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobRepository;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaleExecutionCleanerTest {

    @Mock
    private JobExplorer explorer;
    @Mock
    private JobRepository jobRepository;

    @DisplayName("STARTED 로 남은 실행과 그 스텝을 FAILED 로 마감한다")
    @Test
    void markStaleExecutionsFailed_startedExecution_marksJobAndStepsFailed() {
        // given
        JobExecution exec = new JobExecution(1L);
        exec.setStatus(BatchStatus.STARTED);
        StepExecution step = exec.createStepExecution("statsExportBqLoadStep");
        step.setStatus(BatchStatus.STARTED);
        when(explorer.findRunningJobExecutions("lolStatsExportJob")).thenReturn(Set.of(exec));

        // when
        int count = new StaleExecutionCleaner(explorer, jobRepository).markStaleExecutionsFailed();

        // then
        assertThat(count).isEqualTo(1);
        assertThat(exec.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(step.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(exec.getEndTime()).isNotNull();
        verify(jobRepository).update(step);
        verify(jobRepository).update(exec);
    }

    @DisplayName("돌고 있는 실행이 없으면 아무것도 바꾸지 않는다")
    @Test
    void markStaleExecutionsFailed_none_returnsZero() {
        // given
        when(explorer.findRunningJobExecutions("lolStatsExportJob")).thenReturn(Set.of());

        // when
        int count = new StaleExecutionCleaner(explorer, jobRepository).markStaleExecutionsFailed();

        // then
        assertThat(count).isZero();
        verifyNoInteractions(jobRepository);
    }
}
