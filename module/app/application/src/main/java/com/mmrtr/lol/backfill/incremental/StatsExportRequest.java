package com.mmrtr.lol.backfill.incremental;

import com.mmrtr.lol.backfill.BackfillFactType;
import com.mmrtr.lol.backfill.ExportJobParameters;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 증분 export 요청. runId 가 범위로 결정되므로 같은 범위의 재실행은 같은 JobInstance 로 멱등하다.
 * exportEpoch 는 identifying 파라미터라 올리면 같은 범위를 새 JobInstance 로 다시 export 한다 — runId 와
 * BigQuery jobId 에는 넣지 않으므로 이미 적재된 part 는 결정적 jobId 로 건너뛴다.
 */
public record StatsExportRequest(
        BackfillFactType factType,
        long startId,
        long endId,
        int season,
        List<String> patchVersions,
        List<Integer> queueIds,
        int exportEpoch
) {

    public StatsExportRequest {
        // 빈 patches 는 잡에서 "전체 패치" 로 해석된다 — 증분은 반드시 고정된 대상 패치로만 돈다
        if (patchVersions == null || patchVersions.isEmpty()) {
            throw new IllegalArgumentException("patchVersions 가 비어 있습니다 (빈 목록은 전체 패치를 뜻한다)");
        }
        if (queueIds == null || queueIds.isEmpty()) {
            throw new IllegalArgumentException("queueIds 가 비어 있습니다");
        }
        patchVersions = List.copyOf(patchVersions);
        queueIds = List.copyOf(queueIds);
    }

    public String runId() {
        return "inc-" + factType.code() + "-" + startId + "-" + endId;
    }

    public JobParameters toJobParameters() {
        return new JobParametersBuilder()
                .addString(ExportJobParameters.KEY_FACT_TYPE, factType.code(), true)
                .addString(ExportJobParameters.KEY_RUN_ID, runId(), true)
                .addLong(ExportJobParameters.KEY_START_ID, startId, true)
                .addLong(ExportJobParameters.KEY_END_ID, endId, true)
                .addLong(ExportJobParameters.KEY_SEASON, (long) season, true)
                .addString(ExportJobParameters.KEY_QUEUE_IDS,
                        queueIds.stream().map(String::valueOf).collect(Collectors.joining(",")), true)
                .addString(ExportJobParameters.KEY_PATCHES, String.join(",", patchVersions), true)
                .addString(ExportJobParameters.KEY_MODE, "incremental", true)
                .addLong(ExportJobParameters.KEY_EXPORT_EPOCH, (long) exportEpoch, true)
                .toJobParameters();
    }
}
