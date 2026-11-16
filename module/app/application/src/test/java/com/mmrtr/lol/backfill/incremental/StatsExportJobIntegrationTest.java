package com.mmrtr.lol.backfill.incremental;

import com.google.cloud.bigquery.TableId;
import com.mmrtr.lol.backfill.BackfillFactType;
import com.mmrtr.lol.backfill.BackfillProperties;
import com.mmrtr.lol.backfill.RiotTestRows;
import com.mmrtr.lol.backfill.gcs.GcsUploader;
import com.mmrtr.lol.backfill.sql.AggregationSql;
import com.mmrtr.lol.backfill.tasklet.ChunkExportTasklet;
import com.mmrtr.lol.backfill.tasklet.ManifestTasklet;
import com.mmrtr.lol.infra.bigquery.config.BigQueryProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * lolStatsExportJob 을 실제 JobRepository(riot Testcontainers PG)로 끝까지 돌린다. GCS·BigQuery 만 목이다.
 * 청크 → 매니페스트 → job ExecutionContext → (재시작 시 복원) → BqLoad 로 이어지는 이음매를 검증한다.
 */
@SpringBatchTest
@SpringBootTest(classes = StatsExportJobIntegrationTest.JobTestConfig.class, properties = {
        "lol.backfill.gcs.bucket=" + StatsExportJobIntegrationTest.BUCKET,
        "lol.backfill.chunk.fetch-size=100",
        "lol.stats-export.chunk.size=" + StatsExportJobIntegrationTest.CHUNK_SIZE,
        "lol.stats-export.chunk.parallelism=2",
        "bigquery.dataset=" + StatsExportJobIntegrationTest.DATASET,
})
@ActiveProfiles("test")
class StatsExportJobIntegrationTest {

    static final String BUCKET = "test-bucket";
    static final String DATASET = "test_ds";
    static final int CHUNK_SIZE = 2;
    private static final String TARGET_PATCH = "16.21";
    private static final String MATCH_ID_PREFIX = "JOB-IT-";

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            TransactionAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            BatchAutoConfiguration.class,
            JacksonAutoConfiguration.class,
    })
    @EnableConfigurationProperties({BackfillProperties.class, BigQueryProperties.class})
    @Import({StatsExportJobConfig.class, ChunkExportTasklet.class, ManifestTasklet.class, BqLoadTasklet.class,
            AggregationSql.class})
    static class JobTestConfig {
    }

    @MockitoBean
    private GcsUploader gcsUploader;

    @MockitoBean
    private BigQueryLoader bigQueryLoader;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier("statsExportJobLauncher")
    private JobLauncher statsExportJobLauncher;

    @Autowired
    @Qualifier("lolStatsExportJob")
    private Job lolStatsExportJob;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<String> openedObjects = Collections.synchronizedList(new ArrayList<>());
    private long startId;
    private long endId;
    private List<Long> targetIds;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(lolStatsExportJob);
        jobLauncherTestUtils.setJobLauncher(statsExportJobLauncher);
        cleanUp();

        // 청크(크기 2): [m1 m2] 대상 2행 / [m3 m4] 대상 밖 → 빈 청크(파일 없음) / [m5] 대상 1행
        RiotTestRows rows = new RiotTestRows(jdbc);
        long m1 = insertMatch(rows, 1, TARGET_PATCH);
        long m2 = insertMatch(rows, 2, TARGET_PATCH);
        insertMatch(rows, 3, "16.20");
        insertMatch(rows, 4, "16.20");
        long m5 = insertMatch(rows, 5, TARGET_PATCH);
        startId = m1;
        endId = m5 + 1;
        targetIds = List.of(m1, m2, m5);
        assertThat(chunkStart(m5)).as("m1 과 m5 는 서로 다른 청크여야 한다").isNotEqualTo(chunkStart(m1));

        when(gcsUploader.openWriteStream(eq(BUCKET), anyString(), anyString())).thenAnswer(inv -> {
            openedObjects.add(inv.getArgument(1));
            return OutputStream.nullOutputStream();
        });
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("처음 실행하면 COMPLETED, 비지 않은 청크의 gs:// URI 로 한 번 Load 하고 적재 행 수를 job EC 에 남긴다")
    void launch_fresh_completesAndLoadsNonEmptyChunks() throws Exception {
        // when
        JobExecution execution = jobLauncherTestUtils.launchJob(params());

        // then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        List<String> uris = capturedLoadUris(1).get(0);
        assertThat(uris).containsExactlyElementsOf(expectedUris());
        verify(bigQueryLoader).load(eq(TableId.of(DATASET, BackfillFactType.MATCH.bqTable())), anyList(),
                eq("stats_export_" + DATASET + "_match_" + startId + "_" + endId));
        assertThat(execution.getExecutionContext().getLong(BqLoadTasklet.CTX_ROWS_LOADED)).isEqualTo(3L);
    }

    @Test
    @DisplayName("파티션 하나가 실패한 뒤 재시작하면 앞 실행에서 끝난 청크와 다시 돈 청크를 모두 Load 한다")
    void restart_afterFailedPartition_loadsChunksFromBothExecutions() throws Exception {
        // given — m5 의 청크 업로드는 첫 시도에서만 실패한다
        String failingObject = objectName(chunkStart(targetIds.get(2)));
        String firstObject = objectName(chunkStart(targetIds.get(0)));
        AtomicBoolean failedOnce = new AtomicBoolean();
        when(gcsUploader.openWriteStream(eq(BUCKET), anyString(), anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(1);
            openedObjects.add(name);
            if (name.equals(failingObject) && failedOnce.compareAndSet(false, true)) {
                throw new IllegalStateException("simulated GCS failure");
            }
            return OutputStream.nullOutputStream();
        });

        // when
        JobExecution first = jobLauncherTestUtils.launchJob(params());
        verify(bigQueryLoader, never()).load(any(), anyList(), anyString());
        JobExecution second = jobLauncherTestUtils.launchJob(params());

        // then
        assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(second.getJobInstance().getInstanceId()).isEqualTo(first.getJobInstance().getInstanceId());
        assertThat(capturedLoadUris(1).get(0)).containsExactlyElementsOf(expectedUris());
        assertThat(openedObjects).as("앞 실행에서 끝난 청크는 다시 export 하지 않는다")
                .filteredOn(firstObject::equals).hasSize(1);
        assertThat(openedObjects).filteredOn(failingObject::equals).hasSize(2);
        assertThat(second.getExecutionContext().getLong(BqLoadTasklet.CTX_ROWS_LOADED)).isEqualTo(3L);
    }

    @Test
    @DisplayName("BigQuery Load 가 실패한 뒤 재시작하면 청크·매니페스트는 다시 돌지 않고 job EC 에서 복원한 같은 URI 로 Load 한다")
    void restart_afterFailedLoad_reusesUrisFromJobContext() throws Exception {
        // given
        doThrow(new IllegalStateException("simulated BigQuery failure"))
                .doNothing()
                .when(bigQueryLoader).load(any(), anyList(), anyString());

        // when
        JobExecution first = jobLauncherTestUtils.launchJob(params());
        int exportsAfterFirst = openedObjects.size();
        JobExecution second = jobLauncherTestUtils.launchJob(params());

        // then
        assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        List<List<String>> loads = capturedLoadUris(2);
        assertThat(loads.get(0)).containsExactlyElementsOf(expectedUris());
        assertThat(loads.get(1)).containsExactlyElementsOf(expectedUris());
        assertThat(openedObjects).as("재시작에서 청크 export 는 다시 돌지 않는다").hasSize(exportsAfterFirst);
        verify(gcsUploader, times(1)).uploadBytes(eq(BUCKET), anyString(), any(byte[].class), anyString());
        assertThat(second.getExecutionContext().getLong(BqLoadTasklet.CTX_ROWS_LOADED)).isEqualTo(3L);
    }

    @Test
    @DisplayName("COMPLETED 된 인스턴스를 같은 파라미터로 다시 실행하면 JobInstanceAlreadyCompleteException")
    void relaunch_completedInstance_throwsAlreadyComplete() throws Exception {
        // given
        JobExecution first = jobLauncherTestUtils.launchJob(params());
        assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // when / then
        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(params()))
                .isInstanceOf(JobInstanceAlreadyCompleteException.class);
        verify(bigQueryLoader, times(1)).load(any(), anyList(), anyString());
    }

    private JobParameters params() {
        return new StatsExportRequest(BackfillFactType.MATCH, startId, endId, 16,
                List.of(TARGET_PATCH), List.of(420), 0).toJobParameters();
    }

    private String runId() {
        return "inc-match-" + startId + "-" + endId;
    }

    private long chunkStart(long id) {
        return startId + ((id - startId) / CHUNK_SIZE) * CHUNK_SIZE;
    }

    private String objectName(long chunkStart) {
        long chunkEnd = Math.min(chunkStart + CHUNK_SIZE, endId);
        return String.format("%s/%s/chunk_%012d_%012d.ndjson.gz",
                "match/incremental", runId(), chunkStart, chunkEnd);
    }

    /** 대상 패치 행이 있는 청크만, 청크 순서대로. */
    private List<String> expectedUris() {
        TreeSet<Long> chunkStarts = new TreeSet<>();
        for (long id : targetIds) {
            chunkStarts.add(chunkStart(id));
        }
        return chunkStarts.stream().map(s -> "gs://" + BUCKET + "/" + objectName(s)).toList();
    }

    @SuppressWarnings("unchecked")
    private List<List<String>> capturedLoadUris(int expectedCalls) {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(bigQueryLoader, times(expectedCalls)).load(any(TableId.class), captor.capture(), anyString());
        return captor.getAllValues().stream().map(uris -> (List<String>) new ArrayList<>(uris)).toList();
    }

    private long insertMatch(RiotTestRows rows, int seq, String patch) {
        Map<String, Object> values = new HashMap<>();
        values.put("match_id", MATCH_ID_PREFIX + seq);
        values.put("season", 16);
        values.put("queue_id", 420);
        values.put("patch_version", patch);
        values.put("average_tier", 6543);
        return rows.insert("match", values);
    }

    /** 이 클래스는 트랜잭션 롤백 없이 커밋하므로, 같은 컨테이너를 쓰는 다른 테스트를 위해 직접 지운다. */
    private void cleanUp() {
        jobRepositoryTestUtils.removeJobExecutions();
        jdbc.update("DELETE FROM match WHERE match_id LIKE ?", MATCH_ID_PREFIX + "%");
    }
}
