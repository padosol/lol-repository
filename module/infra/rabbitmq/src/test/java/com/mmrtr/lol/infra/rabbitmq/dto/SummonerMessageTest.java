package com.mmrtr.lol.infra.rabbitmq.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SummonerMessageTest {

    private final Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();

    @Test
    @DisplayName("옛 메시지의 background 필드는 무시하고 읽는다")
    void deserialize_oldJsonWithBackgroundField_ignoresUnknown() {
        String json = "{\"platformId\":\"KR\",\"puuid\":\"p1\",\"revisionDate\":0,\"background\":true}";

        SummonerMessage message = read(json);

        assertThat(message.getPuuid()).isEqualTo("p1");
        assertThat(message.getRenewalId()).isNull();
    }

    @Test
    @DisplayName("renewalId·attempt 가 없는 메시지는 renewalId null, attempt 0 으로 읽는다")
    void missingRenewalFields_deserializeToDefaults() {
        SummonerMessage message = read("{\"platformId\":\"KR\",\"puuid\":\"p\",\"revisionDate\":1}");

        assertThat(message.getRenewalId()).isNull();
        assertThat(message.getAttempt()).isZero();
    }

    @Test
    @DisplayName("renewalId·attempt 는 직렬화·역직렬화 후에도 유지되고 nextAttempt 는 시도 수만 올린다")
    void renewalFields_roundTrip_andNextAttempt() {
        Message sent = converter.toMessage(
                new SummonerMessage("KR", "p", 1L, "r1", 2).nextAttempt(), new MessageProperties());

        SummonerMessage message = read(new String(sent.getBody(), StandardCharsets.UTF_8));

        assertThat(message.getRenewalId()).isEqualTo("r1");
        assertThat(message.getAttempt()).isEqualTo(3);
        assertThat(message.getPuuid()).isEqualTo("p");
        assertThat(message.getRevisionDate()).isEqualTo(1L);
    }

    private SummonerMessage read(String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setInferredArgumentType(SummonerMessage.class);
        return (SummonerMessage) converter.fromMessage(new Message(json.getBytes(StandardCharsets.UTF_8), properties));
    }
}
