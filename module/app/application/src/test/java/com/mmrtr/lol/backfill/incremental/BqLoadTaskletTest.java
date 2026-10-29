package com.mmrtr.lol.backfill.incremental;

import com.google.cloud.bigquery.TableId;
import com.mmrtr.lol.backfill.tasklet.ManifestTasklet;
import com.mmrtr.lol.infra.bigquery.config.BigQueryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
        jobExecution = new JobExecution(1L);
        tasklet = new BqLoadTasklet(loader, new BigQueryProperties(null, "ds", null),
                "match", 100L, 200L, jobExecution);
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

    @DisplayName("행이 있으면 결정적 base jobId 로 Load 하고 행 수를 남긴다")
    @Test
    void execute_withRows_loadsWithDeterministicJobId() {
        // given
        ArrayList<String> uris = new ArrayList<>(List.of("gs://b/a.ndjson.gz"));
        jobExecution.getExecutionContext().put(ManifestTasklet.CTX_SOURCE_URIS, uris);
        jobExecution.getExecutionContext().putLong(ManifestTasklet.CTX_TOTAL_ROWS, 42L);

        // when
        tasklet.execute(null, null);

        // then
        verify(loader).load(TableId.of("ds", "match_fact"), uris, "stats_export_match_100_200");
        assertThat(jobExecution.getExecutionContext().getLong(BqLoadTasklet.CTX_ROWS_LOADED)).isEqualTo(42L);
    }
}
