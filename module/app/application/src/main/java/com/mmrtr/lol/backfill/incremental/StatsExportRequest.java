package com.mmrtr.lol.backfill.incremental;

import com.mmrtr.lol.backfill.BackfillFactType;
import com.mmrtr.lol.backfill.ExportJobParameters;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 증분 export 요청. runId 가 범위로 결정되므로 같은 범위의 재실행은 같은 JobInstance 로 멱등하다.
 */
public record StatsExportRequest(
        BackfillFactType factType,
        long startId,
        long endId,
        int season,
        List<String> patchVersions,
        List<Integer> queueIds
) {

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
                .toJobParameters();
    }
}
