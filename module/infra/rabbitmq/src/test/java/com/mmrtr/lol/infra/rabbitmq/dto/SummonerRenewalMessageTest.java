package com.mmrtr.lol.infra.rabbitmq.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SummonerRenewalMessageTest {

    @Test
    @DisplayName("startOffset 이 없으면 20 으로 본다")
    void startOffsetOrDefault_missing_20() {
        // given // when // then
        assertThat(new SummonerRenewalMessage("p", "KR", 0L).startOffsetOrDefault()).isEqualTo(20);
        assertThat(new SummonerRenewalMessage("p", "KR", 0L, 0).startOffsetOrDefault()).isEqualTo(0);
    }

    @Test
    @DisplayName("RenewalStepMessage.nextAttempt 는 시도 수만 하나 올린다")
    void nextAttempt_incrementsOnly() {
        // given
        RenewalStepMessage message = new RenewalStepMessage("r", "p", "KR", 100L, 1);

        // when
        RenewalStepMessage next = message.nextAttempt();

        // then
        assertThat(next).isEqualTo(new RenewalStepMessage("r", "p", "KR", 100L, 2));
    }
}
