package com.mmrtr.lol.backfill.tasklet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmrtr.lol.backfill.BackfillFactType;
import com.mmrtr.lol.backfill.BackfillProperties;
import com.mmrtr.lol.backfill.ExportJobParameters;
import com.mmrtr.lol.backfill.gcs.GcsUploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 파티션 스텝들이 모두 끝난 뒤, 해당 run_id 하위 청크들의 메타데이터를
 * _manifest.json 으로 묶어 GCS 에 업로드한다. 후속 BQ Load Job 이 이
 * 매니페스트를 읽어 파일 목록 / 스키마 버전을 확인한다.
 */
@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class ManifestTasklet implements Tasklet {

    public static final String CTX_SOURCE_URIS = "manifestSourceUris";
    public static final String CTX_TOTAL_ROWS = "manifestTotalRows";

    private static final String SCHEMA_VERSION = "v1";

    private final JobExplorer jobExplorer;
    private final BackfillProperties properties;
    private final GcsUploader gcsUploader;
    private final ObjectMapper objectMapper;

    @Value("#{jobParameters['runId']}")
    private String runId;

    @Value("#{jobParameters['factType']}")
    private String factTypeCode;

    @Value("#{stepExecution.jobExecution}")
    private JobExecution jobExecution;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        BackfillFactType factType = BackfillFactType.fromCode(factTypeCode);
        ExportJobParameters exportParams = ExportJobParameters.resolve(jobExecution.getJobParameters(), properties);
        List<StepExecution> allSteps = new ArrayList<>();
        for (JobExecution execution : jobExplorer.getJobExecutions(jobExecution.getJobInstance())) {
            allSteps.addAll(execution.getStepExecutions());
        }
        List<ManifestChunkCollector.ChunkEntry> chunks = ManifestChunkCollector.collect(allSteps);
        long totalRows = chunks.stream().mapToLong(ManifestChunkCollector.ChunkEntry::rows).sum();
        List<Map<String, Object>> chunkMaps = chunks.stream()
                .map(c -> Map.<String, Object>of("step", c.step(), "gcsObject", c.gcsObject(), "rows", c.rows()))
                .toList();

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("runId", runId);
        manifest.put("factType", factType.code());
        manifest.put("schemaVersion", SCHEMA_VERSION);
        manifest.put("mode", exportParams.mode().name());
        manifest.put("writtenAt", Instant.now().toString());
        manifest.put("filter", Map.of(
                "season", exportParams.season(),
                "queueIds", exportParams.queueIds()));
        manifest.put("patches", exportParams.patchVersions());
        manifest.put("range", Map.of(
                "startId", exportParams.startId(),
                "endId", exportParams.endId()));
        manifest.put("chunkCount", chunks.size());
        manifest.put("totalRows", totalRows);
        manifest.put("chunks", chunkMaps);

        byte[] content = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(manifest);
        String bucket = properties.gcs().bucket();
        String objectName = String.format("%s/%s/_manifest.json",
                factType.gcsPrefixFor(exportParams.mode()), runId);
        gcsUploader.uploadBytes(bucket, objectName, content, "application/json");

        ArrayList<String> sourceUris = new ArrayList<>();
        for (ManifestChunkCollector.ChunkEntry c : chunks) {
            sourceUris.add("gs://" + bucket + "/" + c.gcsObject());
        }
        ExecutionContext jobCtx = jobExecution.getExecutionContext();
        jobCtx.put(CTX_SOURCE_URIS, sourceUris);
        jobCtx.putLong(CTX_TOTAL_ROWS, totalRows);

        log.info("Manifest uploaded: gs://{}/{} chunks={} totalRows={}", bucket, objectName, chunks.size(), totalRows);
        return RepeatStatus.FINISHED;
    }
}
