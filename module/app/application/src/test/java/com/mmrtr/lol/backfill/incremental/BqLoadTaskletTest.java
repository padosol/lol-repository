package com.mmrtr.lol.backfill.incremental;

import com.google.cloud.bigquery.TableId;
import com.mmrtr.lol.backfill.BackfillProperties;
import com.mmrtr.lol.backfill.tasklet.ManifestTasklet;
import com.mmrtr.lol.infra.bigquery.config.BigQueryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("BqLoadTasklet")
class BqLoadTaskletTest {

    private BigQueryLoader loader;
    private JobExecution jobExecution;
    private BqLoadTasklet tasklet;

    @BeforeEach
    void setUp() {
        loader = mock(BigQueryLoader.class);
        jobExecution = new JobExecution(1L, new JobParametersBuilder()
                .addLong("startId", 100L).addLong("endId", 200L).toJobParameters());
        tasklet = newTasklet(jobExecution);
    }

    private BqLoadTasklet newTasklet(JobExecution execution) {
        return new BqLoadTasklet(loader, new BigQueryProperties(null, "ds", null), "match", backfill(), execution);
    }

    private static BackfillProperties backfill() {
        return new BackfillProperties(
                new BackfillProperties.Range(null, null),
                new BackfillProperties.Chunk(1000, 2, 100),
                new BackfillProperties.Filter(16, List.of(420)),
                new BackfillProperties.Gcs("b"));
    }

    @DisplayName("startId/endId 가 없으면 결정적 jobId 를 만들 수 없어 예외")
    @Test
    void execute_missingRange_throws() {
        // given
        JobExecution noRange = new JobExecution(2L, new JobParameters());
        noRange.getExecutionContext().put(ManifestTasklet.CTX_SOURCE_URIS, new ArrayList<>(List.of("gs://b/a")));
        noRange.getExecutionContext().putLong(ManifestTasklet.CTX_TOTAL_ROWS, 5L);

        // when / then
        assertThatThrownBy(() -> newTasklet(noRange).execute(null, null))
                .isInstanceOf(IllegalStateException.class);
        verify(loader, never()).load(any(), anyList(), anyString());
    }

    @DisplayName("행이 0 이면 Load 를 호출하지 않고 적재 행 수 0 을 남긴다")
    @Test
    void execute_zeroRows_skipsLoad() {
        // given
        jobExecution.getExecutionContext().putLong(ManifestTasklet.CTX_TOTAL_ROWS, 0L);

        // when
        tasklet.execute(null, null);

        // then
        verify(loader, never()).load(any(), anyList(), anyString());
        assertThat(jobExecution.getExecutionContext().getLong(BqLoadTasklet.CTX_ROWS_LOADED)).isZero();
    }

    @DisplayName("행이 있으면 데이터셋을 포함한 결정적 base jobId 로 Load 하고 행 수를 남긴다")
    @Test
    void execute_withRows_loadsWithDeterministicJobId() {
        // given
        ArrayList<String> uris = new ArrayList<>(List.of("gs://b/a.ndjson.gz"));
        jobExecution.getExecutionContext().put(ManifestTasklet.CTX_SOURCE_URIS, uris);
        jobExecution.getExecutionContext().putLong(ManifestTasklet.CTX_TOTAL_ROWS, 42L);

        // when
        tasklet.execute(null, null);

        // then
        verify(loader).load(TableId.of("ds", "match_fact"), uris, "stats_export_ds_match_100_200");
        assertThat(jobExecution.getExecutionContext().getLong(BqLoadTasklet.CTX_ROWS_LOADED)).isEqualTo(42L);
    }

    @DisplayName("같은 범위라도 데이터셋이 다르면 base jobId 가 달라 환경 간 jobId 가 겹치지 않는다")
    @Test
    void execute_differentDataset_differentBaseJobId() {
        // given
        ArrayList<String> uris = new ArrayList<>(List.of("gs://b/a.ndjson.gz"));
        jobExecution.getExecutionContext().put(ManifestTasklet.CTX_SOURCE_URIS, uris);
        jobExecution.getExecutionContext().putLong(ManifestTasklet.CTX_TOTAL_ROWS, 42L);
        BqLoadTasklet local = new BqLoadTasklet(loader, new BigQueryProperties("proj", "lol_local", null),
                "match-ban", backfill(), jobExecution);

        // when
        local.execute(null, null);

        // then
        verify(loader).load(TableId.of("proj", "lol_local", "match_ban_fact"), uris,
                "stats_export_lol_local_match-ban_100_200");
    }
}
