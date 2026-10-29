package com.mmrtr.lol.backfill.incremental;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryError;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.JobStatus;
import com.google.cloud.bigquery.LoadJobConfiguration;
import com.google.cloud.bigquery.TableId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BigQueryLoader")
class BigQueryLoaderTest {

    private static final TableId TABLE = TableId.of("ds", "match_fact");
    private static final List<String> ONE_URI = List.of("gs://b/x.ndjson.gz");

    private BigQuery bigQuery;
    private BigQueryLoader loader;

    @BeforeEach
    void setUp() {
        bigQuery = mock(BigQuery.class);
        loader = new BigQueryLoader(bigQuery, new StatsExportProperties(null, null, 0, List.of(), 0L, null, null));
    }

    private Job doneJob(boolean failed) throws Exception {
        Job job = mock(Job.class);
        JobStatus status = mock(JobStatus.class);
        when(status.getError()).thenReturn(failed ? new BigQueryError("invalid", "loc", "boom") : null);
        when(job.getStatus()).thenReturn(status);
        when(job.waitFor()).thenReturn(job);
        return job;
    }

    private void stubGetJob(String id, Job job) {
        when(bigQuery.getJob(argThat((JobId j) -> j != null && id.equals(j.getJob())))).thenReturn(job);
    }

    @DisplayName("이전 시도가 없으면 a0 으로 APPEND·CREATE_NEVER·maxBadRecords 0 Load 를 제출한다")
    @Test
    void load_noPreviousJob_submitsAttemptZero() throws Exception {
        // given
        when(bigQuery.getJob(any(JobId.class))).thenReturn(null);
        Job job = doneJob(false);
        when(bigQuery.create(any(JobInfo.class))).thenReturn(job);

        // when
        loader.load(TABLE, ONE_URI, "base");

        // then
        ArgumentCaptor<JobInfo> captured = ArgumentCaptor.forClass(JobInfo.class);
        verify(bigQuery).create(captured.capture());
        JobInfo info = captured.getValue();
        assertThat(info.getJobId().getJob()).isEqualTo("base_p0_a0");
        LoadJobConfiguration cfg = info.getConfiguration();
        assertThat(cfg.getWriteDisposition()).isEqualTo(JobInfo.WriteDisposition.WRITE_APPEND);
        assertThat(cfg.getCreateDisposition()).isEqualTo(JobInfo.CreateDisposition.CREATE_NEVER);
        assertThat(cfg.getMaxBadRecords()).isZero();
        assertThat(cfg.getFormat()).isEqualTo("NEWLINE_DELIMITED_JSON");
        assertThat(cfg.getSourceUris()).containsExactly("gs://b/x.ndjson.gz");
    }

    @DisplayName("이미 성공한 시도가 있으면 다시 제출하지 않는다")
    @Test
    void load_attemptAlreadySucceeded_doesNotResubmit() throws Exception {
        // given
        Job job = doneJob(false);
        when(bigQuery.getJob(any(JobId.class))).thenReturn(job);

        // when
        loader.load(TABLE, ONE_URI, "base");

        // then
        verify(bigQuery, never()).create(any(JobInfo.class));
    }

    @DisplayName("앞 시도가 실패했으면 다음 번호로 제출한다")
    @Test
    void load_previousAttemptFailed_submitsNextAttempt() throws Exception {
        // given
        when(bigQuery.getJob(any(JobId.class))).thenReturn(null);
        Job failed = doneJob(true);
        stubGetJob("base_p0_a0", failed);
        Job job = doneJob(false);
        when(bigQuery.create(any(JobInfo.class))).thenReturn(job);

        // when
        loader.load(TABLE, ONE_URI, "base");

        // then
        ArgumentCaptor<JobInfo> captured = ArgumentCaptor.forClass(JobInfo.class);
        verify(bigQuery).create(captured.capture());
        assertThat(captured.getValue().getJobId().getJob()).isEqualTo("base_p0_a1");
    }

    @DisplayName("제출한 Load 가 실패하면 예외")
    @Test
    void load_submittedJobFails_throws() throws Exception {
        // given
        when(bigQuery.getJob(any(JobId.class))).thenReturn(null);
        Job job = doneJob(true);
        when(bigQuery.create(any(JobInfo.class))).thenReturn(job);

        // when / then
        assertThatThrownBy(() -> loader.load(TABLE, ONE_URI, "base"))
                .isInstanceOf(IllegalStateException.class);
    }

    @DisplayName("10개 시도가 모두 실패면 예외")
    @Test
    void load_allAttemptsFailed_throws() throws Exception {
        // given
        Job job = doneJob(true);
        when(bigQuery.getJob(any(JobId.class))).thenReturn(job);

        // when / then
        assertThatThrownBy(() -> loader.load(TABLE, ONE_URI, "base"))
                .isInstanceOf(IllegalStateException.class);
        verify(bigQuery, never()).create(any(JobInfo.class));
    }

    @DisplayName("URI 가 10,000 개를 넘으면 part 를 나눠 각각 제출한다")
    @Test
    void load_moreThan10000Uris_splitsIntoParts() throws Exception {
        // given
        List<String> uris = new ArrayList<>();
        for (int i = 0; i < 10_001; i++) {
            uris.add("gs://b/" + i + ".ndjson.gz");
        }
        when(bigQuery.getJob(any(JobId.class))).thenReturn(null);
        Job job = doneJob(false);
        when(bigQuery.create(any(JobInfo.class))).thenReturn(job);

        // when
        loader.load(TABLE, uris, "base");

        // then
        ArgumentCaptor<JobInfo> captured = ArgumentCaptor.forClass(JobInfo.class);
        verify(bigQuery, times(2)).create(captured.capture());
        JobInfo p0 = captured.getAllValues().get(0);
        JobInfo p1 = captured.getAllValues().get(1);
        assertThat(p0.getJobId().getJob()).isEqualTo("base_p0_a0");
        assertThat(((LoadJobConfiguration) p0.getConfiguration()).getSourceUris()).hasSize(10_000);
        assertThat(p1.getJobId().getJob()).isEqualTo("base_p1_a0");
        assertThat(((LoadJobConfiguration) p1.getConfiguration()).getSourceUris()).hasSize(1);
    }
}
