package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.support.riot.RiotCallPriority;

/**
 * ⑥ 매치 적재 우선순위 큐 mmrtr.matchId.priority 의 처리 순위. 순위가 낮을수록(1) 먼저 꺼내고, AMQP priority 는 반대로 클수록 먼저다.
 */
public enum MatchQueueRank {
    TIMELINE_LATER(1, 3, RiotCallPriority.DEFERRED),
    USER_RETRY(2, 2, RiotCallPriority.USER),
    BACKFILL(3, 1, RiotCallPriority.BACKGROUND);

    private final int rank;
    private final int amqpPriority;
    private final RiotCallPriority riotPriority;

    MatchQueueRank(int rank, int amqpPriority, RiotCallPriority riotPriority) {
        this.rank = rank;
        this.amqpPriority = amqpPriority;
        this.riotPriority = riotPriority;
    }

    public int rank() {
        return rank;
    }

    public int amqpPriority() {
        return amqpPriority;
    }

    public RiotCallPriority riotPriority() {
        return riotPriority;
    }

    /** priority 가 없거나 모르는 값이면 가장 낮은 순위(백필)로 본다. */
    public static MatchQueueRank fromAmqpPriority(Integer priority) {
        if (priority != null) {
            for (MatchQueueRank value : values()) {
                if (value.amqpPriority == priority) {
                    return value;
                }
            }
        }
        return BACKFILL;
    }
}
