package com.mmrtr.lol.backfill.tasklet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmrtr.lol.backfill.BackfillFactType;
import com.mmrtr.lol.backfill.BackfillProperties;
import com.mmrtr.lol.backfill.gcs.GcsUploader;
import com.mmrtr.lol.backfill.partition.IdRangePartitioner;
import com.mmrtr.lol.backfill.sql.AggregationSql;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * 단일 청크(match_participant_id 범위)를 PG 에서 스트리밍으로 읽어
 * NDJSON.gz 로 직렬화한 뒤 GCS 에 업로드한다. 행 수가 0 이면 파일을 만들지 않는다.
 */
@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class ChunkExportTasklet implements Tasklet {

    public static final String CTX_ROWS_WRITTEN = "rowsWritten";
    public static final String CTX_GCS_OBJECT = "gcsObject";

    private final DataSource dataSource;
    private final AggregationSql aggregationSql;
    private final BackfillProperties properties;
    private final GcsUploader gcsUploader;
    private final ObjectMapper objectMapper;

    @Value("#{stepExecutionContext['" + IdRangePartitioner.CTX_START_ID + "']}")
    private Long startId;

    @Value("#{stepExecutionContext['" + IdRangePartitioner.CTX_END_ID + "']}")
    private Long endId;

    @Value("#{jobParameters['runId']}")
    private String runId;

    @Value("#{jobParameters['factType']}")
    private String factTypeCode;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        BackfillFactType factType = BackfillFactType.fromCode(factTypeCode);
        String objectName = buildObjectName(factType);
        long rowsWritten = exportToGcs(objectName, factType);

        ExecutionContext stepCtx = chunkContext.getStepContext()
                .getStepExecution()
                .getExecutionContext();
        stepCtx.putLong(CTX_ROWS_WRITTEN, rowsWritten);
        if (rowsWritten > 0) {
            stepCtx.putString(CTX_GCS_OBJECT, objectName);
        }

        log.info("Chunk {}-{} exported: rows={} gcs={}",
                startId, endId, rowsWritten, rowsWritten > 0 ? objectName : "<skipped-empty>");
        return RepeatStatus.FINISHED;
    }

    private String buildObjectName(BackfillFactType factType) {
        return String.format("%s/%s/chunk_%012d_%012d.ndjson.gz",
                factType.gcsPrefix(), runId, startId, endId);
    }

    private long exportToGcs(String objectName, BackfillFactType factType) throws IOException {
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        jdbc.getJdbcTemplate().setFetchSize(properties.chunk().fetchSize());

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("startId", startId)
                .addValue("endId", endId)
                .addValue("season", properties.filter().season())
                .addValue("queueIds", properties.filter().queueIds());

        long[] count = {0};
        OutputStream raw = gcsUploader.openWriteStream(
                properties.gcs().bucket(), objectName, "application/x-ndjson");
        try (OutputStream out = raw;
             GZIPOutputStream gzip = new GZIPOutputStream(out);
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(gzip, StandardCharsets.UTF_8))) {

            jdbc.query(aggregationSql.get(factType), params, rs -> {
                try {
                    writer.write(rowToJson(rs));
                    writer.newLine();
                    count[0]++;
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to serialize row", e);
                }
            });
        }
        return count[0];
    }

    private String rowToJson(ResultSet rs) throws SQLException, IOException {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        Map<String, Object> row = new LinkedHashMap<>(cols);
        for (int i = 1; i <= cols; i++) {
            String name = md.getColumnLabel(i);
            Object value = rs.getObject(i);
            if (value instanceof java.sql.Array arr) {
                row.put(name, arr.getArray());
            } else {
                row.put(name, value);
            }
        }
        return objectMapper.writeValueAsString(row);
    }
}
