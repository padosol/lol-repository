package com.mmrtr.lol.backfill;

import com.mmrtr.lol.backfill.config.BackfillJobConfig;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/backfill/{factType}")
public class BackfillController {

    private final JobLauncher jobLauncher;
    private final Job job;
    private final BackfillProgressService progressService;

    public BackfillController(
            @Qualifier("backfillJobLauncher") JobLauncher jobLauncher,
            Job lolBackfillJob,
            BackfillProgressService progressService
    ) {
        this.jobLauncher = jobLauncher;
        this.job = lolBackfillJob;
        this.progressService = progressService;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run(
            @PathVariable String factType,
            @RequestBody(required = false) RunRequest request
    ) {
        BackfillFactType type = BackfillFactType.fromCode(factType);
        String runId = (request != null && request.runId() != null)
                ? request.runId()
                : "run-" + Instant.now().toEpochMilli();

        JobParametersBuilder builder = new JobParametersBuilder()
                .addString("factType", type.code(), true)
                .addString("runId", runId, true);
        if (request != null) {
            if (request.startId() != null) {
                builder.addLong("startId", request.startId(), true);
            }
            if (request.endId() != null) {
                builder.addLong("endId", request.endId(), true);
            }
        }

        return launch(type, runId, builder.toJobParameters());
    }

    /**
     * 이전 실행의 jobParameters(factType/runId/startId/endId) 를 그대로 재사용해서 재실행.
     * Spring Batch 가 같은 JobInstance 로 인식하여 COMPLETED 청크는 자동 스킵.
     */
    @PostMapping("/runs/{runId}/resume")
    public ResponseEntity<Map<String, Object>> resume(
            @PathVariable String factType,
            @PathVariable String runId
    ) {
        BackfillFactType type = BackfillFactType.fromCode(factType);
        return progressService.findLatestExecutionByRunId(type, runId)
                .map(prev -> launch(type, runId, prev.getJobParameters()))
                .orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<Map<String, Object>> launch(
            BackfillFactType type,
            String runId,
            JobParameters params
    ) {
        try {
            JobExecution execution = jobLauncher.run(job, params);
            return ResponseEntity.ok(Map.of(
                    "jobName", BackfillJobConfig.JOB_NAME,
                    "factType", type.code(),
                    "runId", runId,
                    "jobExecutionId", execution.getId(),
                    "status", execution.getStatus().toString()
            ));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to launch backfill job: " + e.getMessage(), e);
        }
    }

    @GetMapping("/runs")
    public List<BackfillProgressService.RunSummary> listRuns(
            @PathVariable String factType,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        return progressService.listRecentRuns(BackfillFactType.fromCode(factType), limit);
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<BackfillProgressService.RunProgress> getRun(
            @PathVariable String factType,
            @PathVariable String runId
    ) {
        return progressService.getProgressByRunId(BackfillFactType.fromCode(factType), runId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/runs/{runId}/failed-chunks")
    public ResponseEntity<List<BackfillProgressService.ChunkProgress>> getFailedChunks(
            @PathVariable String factType,
            @PathVariable String runId
    ) {
        return progressService.getProgressByRunId(BackfillFactType.fromCode(factType), runId)
                .map(progress -> progress.chunks().stream()
                        .filter(BackfillProgressService.ChunkProgress::isFailed)
                        .toList())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/executions/{jobExecutionId}")
    public ResponseEntity<BackfillProgressService.RunProgress> getExecution(
            @PathVariable String factType,
            @PathVariable long jobExecutionId
    ) {
        BackfillFactType.fromCode(factType);
        return progressService.getProgressByExecutionId(jobExecutionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record RunRequest(String runId, Long startId, Long endId) {
    }
}
