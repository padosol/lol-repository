package com.mmrtr.lol.backfill;

import com.mmrtr.lol.backfill.config.BackfillJobConfig;
import com.mmrtr.lol.backfill.partition.IdRangePartitioner;
import com.mmrtr.lol.backfill.tasklet.ChunkExportTasklet;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Spring Batch JobExplorer 기반 백필 진행 상황 조회.
 * <p>
 * BATCH_STEP_EXECUTION_CONTEXT.SHORT_CONTEXT 가 Java 직렬화 형태라
 * SQL 직접 파싱이 불가능. JobExplorer 가 직렬화 형식과 무관하게 자동 역직렬화하므로
 * 이 API 를 통해서만 조회.
 */
@Service
@RequiredArgsConstructor
public class BackfillProgressService {

    private static final int RECENT_INSTANCES_LIMIT = 200;

    private final JobExplorer jobExplorer;

    /**
     * runId 로 가장 최근 JobExecution 의 진행 상황 조회.
     */
    public Optional<RunProgress> getProgressByRunId(String runId) {
        return findLatestExecutionByRunId(runId).map(this::toProgress);
    }

    /**
     * jobExecutionId 로 직접 진행 상황 조회.
     */
    public Optional<RunProgress> getProgressByExecutionId(long jobExecutionId) {
        return Optional.ofNullable(jobExplorer.getJobExecution(jobExecutionId)).map(this::toProgress);
    }

    /**
     * 최근 실행된 N 개 run 요약.
     */
    public List<RunSummary> listRecentRuns(int limit) {
        return jobExplorer
                .getJobInstances(BackfillJobConfig.JOB_NAME, 0, Math.min(limit, RECENT_INSTANCES_LIMIT))
                .stream()
                .flatMap(ji -> jobExplorer.getJobExecutions(ji).stream())
                .sorted(Comparator.comparing(JobExecution::getCreateTime).reversed())
                .limit(limit)
                .map(this::toSummary)
                .toList();
    }

    /**
     * runId 의 가장 최근 JobExecution 조회 (재시작 / 파라미터 재사용 용도).
     */
    public Optional<JobExecution> findLatestExecutionByRunId(String runId) {
        return jobExplorer
                .getJobInstances(BackfillJobConfig.JOB_NAME, 0, RECENT_INSTANCES_LIMIT)
                .stream()
                .flatMap(ji -> jobExplorer.getJobExecutions(ji).stream())
                .filter(je -> runId.equals(je.getJobParameters().getString("runId")))
                .max(Comparator.comparing(JobExecution::getId));
    }

    private RunProgress toProgress(JobExecution exec) {
        List<ChunkProgress> chunks = exec.getStepExecutions().stream()
                .filter(this::isChunkWorkerStep)
                .sorted(Comparator.comparing(StepExecution::getStepName))
                .map(this::toChunk)
                .toList();

        Map<String, Long> statusCount = chunks.stream()
                .collect(Collectors.groupingBy(ChunkProgress::status, Collectors.counting()));

        long totalRows = chunks.stream()
                .mapToLong(c -> c.rowsWritten() != null ? c.rowsWritten() : 0L)
                .sum();

        return new RunProgress(
                exec.getJobParameters().getString("runId"),
                exec.getId(),
                exec.getStatus().name(),
                exec.getCreateTime(),
                exec.getStartTime(),
                exec.getEndTime(),
                chunks.size(),
                statusCount,
                totalRows,
                chunks
        );
    }

    private RunSummary toSummary(JobExecution exec) {
        List<StepExecution> chunkSteps = exec.getStepExecutions().stream()
                .filter(this::isChunkWorkerStep)
                .toList();

        Map<String, Long> statusCount = chunkSteps.stream()
                .collect(Collectors.groupingBy(se -> se.getStatus().name(), Collectors.counting()));

        return new RunSummary(
                exec.getJobParameters().getString("runId"),
                exec.getId(),
                exec.getStatus().name(),
                exec.getCreateTime(),
                exec.getStartTime(),
                exec.getEndTime(),
                chunkSteps.size(),
                statusCount
        );
    }

    private boolean isChunkWorkerStep(StepExecution se) {
        // IdRangePartitioner 가 partition key 를 "chunk_<from>_<to>" 형태로 만듬.
        // partition step 자체("mpBuildPartitionStep") / manifest step 은 제외.
        return se.getStepName() != null && se.getStepName().startsWith("chunk_");
    }

    private ChunkProgress toChunk(StepExecution se) {
        ExecutionContext ctx = se.getExecutionContext();
        return new ChunkProgress(
                se.getStepName(),
                se.getStatus().name(),
                ctx.containsKey(IdRangePartitioner.CTX_START_ID) ? ctx.getLong(IdRangePartitioner.CTX_START_ID) : null,
                ctx.containsKey(IdRangePartitioner.CTX_END_ID) ? ctx.getLong(IdRangePartitioner.CTX_END_ID) : null,
                ctx.containsKey(ChunkExportTasklet.CTX_ROWS_WRITTEN) ? ctx.getLong(ChunkExportTasklet.CTX_ROWS_WRITTEN) : null,
                ctx.containsKey(ChunkExportTasklet.CTX_GCS_OBJECT) ? ctx.getString(ChunkExportTasklet.CTX_GCS_OBJECT) : null,
                se.getStartTime(),
                se.getEndTime(),
                se.getExitStatus().getExitDescription()
        );
    }

    /**
     * 단일 run 의 전체 진행 상황. chunks 는 startId 오름차순으로 정렬된 청크별 상세.
     */
    public record RunProgress(
            String runId,
            Long jobExecutionId,
            String jobStatus,
            LocalDateTime createTime,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int totalChunks,
            Map<String, Long> statusCount,
            long totalRowsWritten,
            List<ChunkProgress> chunks
    ) {
    }

    public record ChunkProgress(
            String stepName,
            String status,
            Long startId,
            Long endId,
            Long rowsWritten,
            String gcsObject,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String exitMessage
    ) {

        public boolean isFailed() {
            return BatchStatus.FAILED.name().equals(status);
        }
    }

    public record RunSummary(
            String runId,
            Long jobExecutionId,
            String jobStatus,
            LocalDateTime createTime,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int totalChunks,
            Map<String, Long> statusCount
    ) {
    }
}
