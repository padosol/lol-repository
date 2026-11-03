package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.support.riot.RiotCallPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.mmrtr.lol.infra.rabbitmq.service.MatchQueueRank.BACKFILL;
import static com.mmrtr.lol.infra.rabbitmq.service.MatchQueueRank.TIMELINE_LATER;
import static com.mmrtr.lol.infra.rabbitmq.service.MatchQueueRank.USER_RETRY;
import static org.assertj.core.api.Assertions.assertThat;

class MatchQueueRankTest {

    @Test
    @DisplayName("AMQP priority 로 순위를 되찾고 모르는 값은 백필로 본다")
    void fromAmqpPriority_mapsAndDefaultsToBackfill() {
        // given // when // then
        assertThat(MatchQueueRank.fromAmqpPriority(3)).isEqualTo(TIMELINE_LATER);
        assertThat(MatchQueueRank.fromAmqpPriority(2)).isEqualTo(USER_RETRY);
        assertThat(MatchQueueRank.fromAmqpPriority(1)).isEqualTo(BACKFILL);
        assertThat(MatchQueueRank.fromAmqpPriority(null)).isEqualTo(BACKFILL);
        assertThat(MatchQueueRank.fromAmqpPriority(9)).isEqualTo(BACKFILL);
    }

    @Test
    @DisplayName("순위마다 Riot 호출 우선순위가 정해져 있다")
    void riotPriority_perRank() {
        // given // when // then
        assertThat(TIMELINE_LATER.riotPriority()).isEqualTo(RiotCallPriority.DEFERRED);
        assertThat(USER_RETRY.riotPriority()).isEqualTo(RiotCallPriority.USER);
        assertThat(BACKFILL.riotPriority()).isEqualTo(RiotCallPriority.BACKGROUND);
        assertThat(USER_RETRY.rank()).isEqualTo(2);
        assertThat(USER_RETRY.amqpPriority()).isEqualTo(2);
    }
}
