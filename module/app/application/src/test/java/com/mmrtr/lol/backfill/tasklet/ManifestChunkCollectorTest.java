package com.mmrtr.lol.backfill.tasklet;

import com.mmrtr.lol.backfill.tasklet.ManifestChunkCollector.ChunkEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManifestChunkCollectorTest {

    private static final JobInstance INSTANCE = new JobInstance(1L, "job");

    private static StepExecution step(JobExecution exec, String name, BatchStatus status, String gcsObject, long rows) {
        StepExecution step = new StepExecution(name, exec);
        step.setStatus(status);
        if (gcsObject != null) {
            step.getExecutionContext().putString(ChunkExportTasklet.CTX_GCS_OBJECT, gcsObject);
            step.getExecutionContext().putLong(ChunkExportTasklet.CTX_ROWS_WRITTEN, rows);
        }
        return step;
    }

    private static JobExecution exec(long id) {
        return new JobExecution(INSTANCE, id, new JobParameters());
    }

    @DisplayName("재시작된 인스턴스: 앞선 실행에서 끝난 청크도 매니페스트에 들어간다")
    @Test
    void collect_restartedInstance_includesChunksCompletedInEarlierExecution() {
        // given
        JobExecution exec1 = exec(1L);
        JobExecution exec2 = exec(2L);
        List<StepExecution> all = new ArrayList<>();
        all.add(step(exec1, "s:chunk_0_100", BatchStatus.COMPLETED, "a", 5));
        all.add(step(exec1, "s:chunk_100_200", BatchStatus.FAILED, null, 0));
        all.add(step(exec2, "s:chunk_100_200", BatchStatus.COMPLETED, "b", 3));

        // when
        List<ChunkEntry> result = ManifestChunkCollector.collect(all);

        // then
        assertThat(result).containsExactly(
                new ChunkEntry("s:chunk_0_100", "a", 5), new ChunkEntry("s:chunk_100_200", "b", 3));
    }

    @DisplayName("한 번도 COMPLETED 되지 않은 파티션이 있으면 실패한다")
    @Test
    void collect_partitionNeverCompleted_throws() {
        // given
        JobExecution exec1 = exec(1L);
        List<StepExecution> all = List.of(
                step(exec1, "s:chunk_0_100", BatchStatus.COMPLETED, "a", 5),
                step(exec1, "s:chunk_100_200", BatchStatus.FAILED, null, 0));

        // when / then
        assertThatThrownBy(() -> ManifestChunkCollector.collect(all))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("s:chunk_100_200");
    }

    @DisplayName("빈 청크(COMPLETED, 파일 없음)는 항목 없이 통과한다")
    @Test
    void collect_emptyChunk_countsAsCompletedWithoutEntry() {
        // given
        List<StepExecution> all = List.of(step(exec(1L), "s:chunk_0_100", BatchStatus.COMPLETED, null, 0));

        // when
        List<ChunkEntry> result = ManifestChunkCollector.collect(all);

        // then
        assertThat(result).isEmpty();
    }

    @DisplayName("매니저·매니페스트 스텝(':' 없음)은 무시한다")
    @Test
    void collect_ignoresNonPartitionSteps() {
        // given
        JobExecution exec1 = exec(1L);
        List<StepExecution> all = List.of(
                step(exec1, "backfillPartitionStep", BatchStatus.FAILED, null, 0),
                step(exec1, "s:chunk_0_100", BatchStatus.COMPLETED, "a", 5));

        // when
        List<ChunkEntry> result = ManifestChunkCollector.collect(all);

        // then
        assertThat(result).containsExactly(new ChunkEntry("s:chunk_0_100", "a", 5));
    }
}
