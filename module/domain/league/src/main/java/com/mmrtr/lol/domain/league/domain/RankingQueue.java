package com.mmrtr.lol.domain.league.domain;

import com.mmrtr.lol.common.type.Queue;

/** 리더보드 큐 선택값. 공개 API 의 {@code queue} 파라미터 값(SOLO, FLEX)이고 저장된 큐 이름으로 바꿔 조회한다. */
public enum RankingQueue {
    SOLO(Queue.RANKED_SOLO_5x5),
    FLEX(Queue.RANKED_FLEX_SR);

    private final Queue queue;

    RankingQueue(Queue queue) {
        this.queue = queue;
    }

    /** {@code summoner_ranking.queue} 에 저장된 값 (예: RANKED_SOLO_5x5). */
    public String queueName() {
        return queue.name();
    }
}
