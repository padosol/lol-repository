package com.mmrtr.lol.backfill.incremental;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Dataset;
import com.google.cloud.bigquery.DatasetId;
import com.google.cloud.bigquery.FormatOptions;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.LoadJobConfiguration;
import com.google.cloud.bigquery.TableId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * NDJSON 파일들을 BigQuery 테이블에 APPEND 한다. jobId 가 결정적이라 크래시 후 재시도해도
 * 이미 성공한 Load 를 다시 제출하지 않는다 (중복 적재 방지).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BigQueryLoader {

    /**
     * 실패한 jobId 는 다시 쓸 수 없어 시도 번호로 넘어간다. 소진되면 그 범위는 영영 적재되지 않으므로 넉넉히 둔다 —
     * 이미 쓴 시도마다 드는 비용은 getJob 조회 1회뿐이다 (2시간 주기면 1000회 ≈ 83일).
     */
    static final int MAX_ATTEMPTS = 1000;
    static final int MAX_URIS_PER_JOB = 10_000;
    private static final int HTTP_CONFLICT = 409;

    private final BigQuery bigQuery;
    private final StatsExportProperties props;
    private volatile String resolvedLocation;

    public static String jobId(String baseJobId, int part, int attempt) {
        return baseJobId + "_p" + part + "_a" + attempt;
    }

    public void load(TableId table, List<String> sourceUris, String baseJobId) {
        int part = 0;
        for (int from = 0; from < sourceUris.size(); from += MAX_URIS_PER_JOB) {
            List<String> uris = sourceUris.subList(from, Math.min(from + MAX_URIS_PER_JOB, sourceUris.size()));
            loadPart(table, uris, baseJobId, part++);
        }
    }

    private void loadPart(TableId table, List<String> uris, String baseJobId, int part) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            JobId jobId = jobIdOf(table, jobId(baseJobId, part, attempt));
            Job existing = bigQuery.getJob(jobId);
            if (existing != null) {
                Job existingDone = await(existing);
                if (existingDone == null) {
                    // 방금 보인 job 이 대기 중 사라졌다 — 성공 여부를 모르므로 다음 시도로 넘기지 않는다(중복 방지)
                    throw new IllegalStateException("BigQuery Load job 이 완료 대기 중 사라짐: " + jobId.getJob());
                }
                if (succeeded(existingDone)) {
                    log.info("BigQuery Load 이미 성공: {}", jobId.getJob());
                    return;
                }
                log.warn("BigQuery Load 이전 시도 실패, 다음 시도로: {}", jobId.getJob());
                continue;
            }
            Job submitted = submit(table, uris, jobId);
            if (submitted == null) {
                // 409: 같은 id 를 이미 제출한 쪽이 있다 — 다시 조회해 판정한다
                // 409 는 이 id 의 job 이 존재한다는 증거 — 보이지 않으면 다음 시도로 넘기지 않고 실패시킨다(중복 방지)
                Job raced = bigQuery.getJob(jobId);
                Job racedDone = raced == null ? null : await(raced);
                if (racedDone == null) {
                    throw new IllegalStateException("BigQuery Load 409 이후 job 을 조회할 수 없음: " + jobId.getJob());
                }
                if (succeeded(racedDone)) {
                    return;
                }
                continue;
            }
            Job done = await(submitted);
            if (!succeeded(done)) {
                throw new IllegalStateException("BigQuery Load 실패: " + jobId.getJob() + " error="
                        + (done == null ? "job vanished" : done.getStatus().getError()));
            }
            log.info("BigQuery Load 완료: {} uris={}", jobId.getJob(), uris.size());
            return;
        }
        throw new IllegalStateException("BigQuery Load " + MAX_ATTEMPTS + "회 시도가 모두 실패: "
                + baseJobId + " part=" + part);
    }

    private Job submit(TableId table, List<String> uris, JobId jobId) {
        LoadJobConfiguration cfg = LoadJobConfiguration.newBuilder(table, uris)
                .setFormatOptions(FormatOptions.json())
                .setWriteDisposition(JobInfo.WriteDisposition.WRITE_APPEND)
                .setCreateDisposition(JobInfo.CreateDisposition.CREATE_NEVER)
                .setMaxBadRecords(0)
                .setIgnoreUnknownValues(false)
                .build();
        try {
            return bigQuery.create(JobInfo.newBuilder(cfg).setJobId(jobId).build());
        } catch (BigQueryException e) {
            if (e.getCode() == HTTP_CONFLICT) {
                return null;
            }
            throw e;
        }
    }

    private Job await(Job job) {
        try {
            return job.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("BigQuery Load 대기 중 인터럽트", e);
        }
    }

    private static boolean succeeded(Job job) {
        return job != null && job.getStatus() != null && job.getStatus().getError() == null;
    }

    private JobId jobIdOf(TableId table, String id) {
        return JobId.newBuilder().setJob(id).setLocation(location(table)).build();
    }

    /** 설정값이 없으면 데이터셋 메타데이터의 location 을 한 번 조회해 쓴다. location 없이는 리전 데이터셋의 getJob 이 404 라 멱등 판정이 불가능하다. */
    private String location(TableId table) {
        if (StringUtils.hasText(props.bqLocation())) {
            return props.bqLocation();
        }
        String cached = resolvedLocation;
        if (cached == null) {
            DatasetId datasetId = table.getProject() == null
                    ? DatasetId.of(table.getDataset())
                    : DatasetId.of(table.getProject(), table.getDataset());
            Dataset dataset = bigQuery.getDataset(datasetId);
            if (dataset == null) {
                throw new IllegalStateException("BigQuery 데이터셋을 찾을 수 없음: " + datasetId);
            }
            cached = dataset.getLocation();
            resolvedLocation = cached;
        }
        return cached;
    }
}
