package com.mmrtr.lol.common.type;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum Queue {
    RANKED_SOLO_5x5(420),
    RANKED_FLEX_SR(440),
    RANKED_FLEX_TT(470);

    private final int queueId;
    private static final Map<Integer, Queue> BY_QUEUE_ID =
            Stream.of(values()).collect(Collectors.toMap(q -> q.queueId, q -> q));

    Queue(int queueId) {
        this.queueId = queueId;
    }

    public int getQueueId() {
        return queueId;
    }

    public static Queue fromQueueId(int queueId) {
        return BY_QUEUE_ID.get(queueId);
    }
}
