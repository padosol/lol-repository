package com.mmrtr.lol.backfill;

import org.springframework.batch.core.JobParameters;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.Arrays;
import java.util.List;

/**
 * export Job 의 실행 파라미터. JobParameters 가 있으면 그것을, 없으면 {@link BackfillProperties} 를 쓴다.
 */
public record ExportJobParameters(
        ExportMode mode,
        int season,
        List<Integer> queueIds,
        List<String> patchVersions,
        Long startId,
        Long endId
) {

    public static final String KEY_FACT_TYPE = "factType";
    public static final String KEY_RUN_ID = "runId";
    public static final String KEY_START_ID = "startId";
    public static final String KEY_END_ID = "endId";
    public static final String KEY_SEASON = "season";
    public static final String KEY_QUEUE_IDS = "queueIds";
    public static final String KEY_PATCHES = "patches";
    public static final String KEY_MODE = "mode";

    public static ExportJobParameters resolve(JobParameters params, BackfillProperties props) {
        Long season = params.getLong(KEY_SEASON);
        String queueIds = params.getString(KEY_QUEUE_IDS);
        Long startId = params.getLong(KEY_START_ID);
        Long endId = params.getLong(KEY_END_ID);
        return new ExportJobParameters(
                ExportMode.fromParam(params.getString(KEY_MODE)),
                season != null ? season.intValue() : props.filter().season(),
                queueIds != null && !queueIds.isBlank()
                        ? split(queueIds).stream().map(Integer::valueOf).toList()
                        : props.filter().queueIds(),
                split(params.getString(KEY_PATCHES)),
                startId != null ? startId : props.range().startId(),
                endId != null ? endId : props.range().endId());
    }

    public boolean allPatches() {
        return patchVersions.isEmpty();
    }

    public MapSqlParameterSource toSqlParams(long chunkStartId, long chunkEndId) {
        return new MapSqlParameterSource()
                .addValue("startId", chunkStartId)
                .addValue("endId", chunkEndId)
                .addValue("season", season)
                .addValue("queueIds", queueIds)
                .addValue("allPatches", allPatches())
                .addValue("patchVersions", allPatches() ? List.of("") : patchVersions);
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
