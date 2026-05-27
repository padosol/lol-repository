package com.mmrtr.lol.backfill.partition;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

import java.util.HashMap;
import java.util.Map;

/**
 * match_participant_id 범위를 청크 단위로 분할.
 * <p>
 * Spring Batch 의 gridSize 파라미터는 "제안치"로만 사용되며, 실제 파티션 수는
 * (endId - startId) / chunkSize 로 결정된다. 각 파티션 컨텍스트에는
 * chunkStartId / chunkEndId (exclusive) / chunkIndex 가 담긴다.
 */
public class IdRangePartitioner implements Partitioner {

    public static final String CTX_START_ID = "chunkStartId";
    public static final String CTX_END_ID = "chunkEndId";
    public static final String CTX_INDEX = "chunkIndex";

    private final long startId;
    private final long endIdExclusive;
    private final long chunkSize;

    public IdRangePartitioner(long startId, long endIdExclusive, long chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive: " + chunkSize);
        }
        if (endIdExclusive <= startId) {
            throw new IllegalArgumentException(
                    "endId must be greater than startId: start=" + startId + " end=" + endIdExclusive);
        }
        this.startId = startId;
        this.endIdExclusive = endIdExclusive;
        this.chunkSize = chunkSize;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Map<String, ExecutionContext> partitions = new HashMap<>();
        int index = 0;
        for (long from = startId; from < endIdExclusive; from += chunkSize) {
            long to = Math.min(from + chunkSize, endIdExclusive);
            ExecutionContext ctx = new ExecutionContext();
            ctx.putLong(CTX_START_ID, from);
            ctx.putLong(CTX_END_ID, to);
            ctx.putInt(CTX_INDEX, index);
            partitions.put(String.format("chunk_%08d_%08d", from, to), ctx);
            index++;
        }
        return partitions;
    }
}
