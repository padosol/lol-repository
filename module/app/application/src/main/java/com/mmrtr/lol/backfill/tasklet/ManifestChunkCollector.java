package com.mmrtr.lol.backfill.tasklet;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ExecutionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JobInstance 의 모든 실행에서 파티션 워커 스텝({step}:{partition})을 모아 매니페스트 항목을 만든다.
 * 재시작 시 앞선 실행에서 COMPLETED 된 파티션은 현재 실행에 스텝 실행이 없으므로 인스턴스 전체를 봐야 한다.
 */
final class ManifestChunkCollector {

    private ManifestChunkCollector() {
    }

    record ChunkEntry(String step, String gcsObject, long rows) {
    }

    static List<ChunkEntry> collect(Collection<StepExecution> stepExecutions) {
        Map<String, StepExecution> lastCompleted = new HashMap<>();
        Map<String, Boolean> seen = new HashMap<>();
        List<StepExecution> ordered = new ArrayList<>(stepExecutions);
        ordered.sort(Comparator.comparing(StepExecution::getJobExecutionId, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(s -> s.getId() == null ? Long.MIN_VALUE : s.getId()));
        for (StepExecution step : ordered) {
            String name = step.getStepName();
            if (!name.contains(":")) {
                continue;
            }
            seen.put(name, Boolean.TRUE);
            if (step.getStatus() == BatchStatus.COMPLETED) {
                lastCompleted.put(name, step);
            }
        }
        for (String name : seen.keySet()) {
            if (!lastCompleted.containsKey(name)) {
                throw new IllegalStateException("COMPLETED 된 적 없는 파티션 스텝이 있습니다: " + name);
            }
        }
        List<ChunkEntry> entries = new ArrayList<>();
        for (Map.Entry<String, StepExecution> e : lastCompleted.entrySet()) {
            ExecutionContext ctx = e.getValue().getExecutionContext();
            if (!ctx.containsKey(ChunkExportTasklet.CTX_GCS_OBJECT)) {
                continue;
            }
            entries.add(new ChunkEntry(e.getKey(),
                    ctx.getString(ChunkExportTasklet.CTX_GCS_OBJECT),
                    ctx.getLong(ChunkExportTasklet.CTX_ROWS_WRITTEN, 0L)));
        }
        entries.sort(Comparator.comparing(ChunkEntry::step));
        return entries;
    }
}
