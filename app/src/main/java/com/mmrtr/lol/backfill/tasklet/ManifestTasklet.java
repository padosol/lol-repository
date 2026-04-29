package com.mmrtr.lol.backfill.tasklet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmrtr.lol.backfill.BackfillProperties;
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
import java.util.Collection;
import java.util.Comparator;
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

    private static final String SCHEMA_VERSION = "v1";

    private final JobExplorer jobExplorer;
    private final BackfillProperties properties;
    private final GcsUploader gcsUploader;
    private final ObjectMapper objectMapper;

    @Value("#{jobParameters['runId']}")
    private String runId;

    @Value("#{stepExecution.jobExecution}")
    private JobExecution jobExecution;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        List<Map<String, Object>> chunks = collectChunkEntries(jobExecution);
        long totalRows = chunks.stream()
                .mapToLong(c -> ((Number) c.get("rows")).longValue())
                .sum();

        Map<String, Object> manifest = Map.of(
                "runId", runId,
                "schemaVersion", SCHEMA_VERSION,
                "writtenAt", Instant.now().toString(),
                "filter", Map.of(
                        "season", properties.filter().season(),
                        "queueIds", properties.filter().queueIds()
                ),
                "range", Map.of(
                        "startId", properties.range().startId(),
                        "endId", properties.range().endId()
                ),
                "chunkCount", chunks.size(),
                "totalRows", totalRows,
                "chunks", chunks
        );

        byte[] content = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(manifest);
        String objectName = String.format("%s/%s/_manifest.json",
                properties.gcs().prefix(), runId);
        gcsUploader.uploadBytes(
                properties.gcs().bucket(), objectName, content, "application/json");

        log.info("Manifest uploaded: gs://{}/{} chunks={} totalRows={}",
                properties.gcs().bucket(), objectName, chunks.size(), totalRows);
        return RepeatStatus.FINISHED;
    }

    private List<Map<String, Object>> collectChunkEntries(JobExecution execution) {
        Collection<StepExecution> steps = execution.getStepExecutions();
        List<Map<String, Object>> entries = new ArrayList<>();
        for (StepExecution step : steps) {
            ExecutionContext ctx = step.getExecutionContext();
            if (!ctx.containsKey(ChunkExportTasklet.CTX_GCS_OBJECT)) {
                continue;
            }
            entries.add(Map.of(
                    "step", step.getStepName(),
                    "gcsObject", ctx.getString(ChunkExportTasklet.CTX_GCS_OBJECT),
                    "rows", ctx.getLong(ChunkExportTasklet.CTX_ROWS_WRITTEN, 0L)
            ));
        }
        entries.sort(Comparator.comparing(m -> (String) m.get("step")));
        return entries;
    }
}
