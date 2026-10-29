package com.mmrtr.lol.backfill.incremental;

import com.google.cloud.bigquery.TableId;
import com.mmrtr.lol.backfill.BackfillFactType;
import com.mmrtr.lol.backfill.tasklet.ManifestTasklet;
import com.mmrtr.lol.infra.bigquery.config.BigQueryProperties;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 매니페스트가 모은 파일들을 BigQuery fact 테이블에 적재한다. 적재 행 수는 job ExecutionContext 에 남긴다.
 */
@Component
@StepScope
public class BqLoadTasklet implements Tasklet {

    public static final String CTX_ROWS_LOADED = "bqRowsLoaded";

    private final BigQueryLoader loader;
    private final BigQueryProperties bigQueryProperties;
    private final String factTypeCode;
    private final Long startId;
    private final Long endId;
    private final JobExecution jobExecution;

    public BqLoadTasklet(BigQueryLoader loader,
                         BigQueryProperties bigQueryProperties,
                         @Value("#{jobParameters['factType']}") String factTypeCode,
                         @Value("#{jobParameters['startId']}") Long startId,
                         @Value("#{jobParameters['endId']}") Long endId,
                         @Value("#{stepExecution.jobExecution}") JobExecution jobExecution) {
        this.loader = loader;
        this.bigQueryProperties = bigQueryProperties;
        this.factTypeCode = factTypeCode;
        this.startId = startId;
        this.endId = endId;
        this.jobExecution = jobExecution;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        ExecutionContext jobCtx = jobExecution.getExecutionContext();
        long totalRows = jobCtx.getLong(ManifestTasklet.CTX_TOTAL_ROWS, 0L);
        if (totalRows == 0) {
            jobCtx.putLong(CTX_ROWS_LOADED, 0L);
            return RepeatStatus.FINISHED;
        }
        BackfillFactType factType = BackfillFactType.fromCode(factTypeCode);
        @SuppressWarnings("unchecked")
        List<String> uris = (List<String>) jobCtx.get(ManifestTasklet.CTX_SOURCE_URIS);
        TableId table = StringUtils.hasText(bigQueryProperties.projectId())
                ? TableId.of(bigQueryProperties.projectId(), bigQueryProperties.dataset(), factType.bqTable())
                : TableId.of(bigQueryProperties.dataset(), factType.bqTable());
        String baseJobId = "stats_export_" + factType.code() + "_" + startId + "_" + endId;
        loader.load(table, uris, baseJobId);
        jobCtx.putLong(CTX_ROWS_LOADED, totalRows);
        return RepeatStatus.FINISHED;
    }
}
