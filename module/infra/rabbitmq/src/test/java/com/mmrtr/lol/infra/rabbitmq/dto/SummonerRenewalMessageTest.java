package com.mmrtr.lol.infra.rabbitmq.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.nio.charset.StandardCharsets;

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

    @Test
    @DisplayName("JSON 에 startOffset 이 없으면 기본 20, 0 이면 0 으로 역직렬화된다")
    void deserialize_throughAppConverter_picksCanonicalConstructor() {
        // given
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();

        // when
        SummonerRenewalMessage without = read(converter,
                "{\"puuid\":\"p\",\"platform\":\"KR\",\"revisionDate\":5}");
        SummonerRenewalMessage zero = read(converter,
                "{\"puuid\":\"p\",\"platform\":\"KR\",\"revisionDate\":5,\"startOffset\":0}");

        // then
        assertThat(without.revisionDate()).isEqualTo(5L);
        assertThat(without.startOffsetOrDefault()).isEqualTo(20);
        assertThat(zero.startOffsetOrDefault()).isEqualTo(0);
    }

    @Test
    @DisplayName("JSON 에 attempt 가 없으면 0, 있으면 그 값으로 역직렬화된다")
    void deserialize_attemptDefaultsToZero() {
        // given
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();

        // when
        SummonerRenewalMessage without = read(converter,
                "{\"puuid\":\"p\",\"platform\":\"KR\",\"revisionDate\":5,\"startOffset\":20}");
        SummonerRenewalMessage retried = read(converter,
                "{\"puuid\":\"p\",\"platform\":\"KR\",\"revisionDate\":5,\"startOffset\":120,\"attempt\":1}");

        // then
        assertThat(without.attempt()).isZero();
        assertThat(retried).isEqualTo(new SummonerRenewalMessage("p", "KR", 5L, 120, 1));
    }

    @Test
    @DisplayName("fromOffset 은 시도 수를 두고 offset 만, nextAttempt 는 offset 과 시도 수 하나를 바꾼다")
    void fromOffsetKeepsAttempt_nextAttemptIncrements() {
        // given
        SummonerRenewalMessage message = new SummonerRenewalMessage("p", "KR", 5L, 20, 1);

        // when // then
        assertThat(message.fromOffset(220)).isEqualTo(new SummonerRenewalMessage("p", "KR", 5L, 220, 1));
        assertThat(message.nextAttempt(120)).isEqualTo(new SummonerRenewalMessage("p", "KR", 5L, 120, 2));
        assertThat(new SummonerRenewalMessage("p", "KR", 5L, 20).attempt()).isZero();
    }

    private SummonerRenewalMessage read(Jackson2JsonMessageConverter converter, String json) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setInferredArgumentType(SummonerRenewalMessage.class);
        return (SummonerRenewalMessage) converter.fromMessage(
                new Message(json.getBytes(StandardCharsets.UTF_8), props));
    }
}
