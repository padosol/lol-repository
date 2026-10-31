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
    @DisplayName("background 필드가 없는 메시지(lol-server·이전 발행자)는 사용자 갱신으로 읽는다")
    void missingBackground_deserializesToFalse() {
        String json = "{\"platformId\":\"KR\",\"puuid\":\"p\",\"revisionDate\":1}";

        SummonerMessage message = read(json);

        assertThat(message.getPuuid()).isEqualTo("p");
        assertThat(message.isBackground()).isFalse();
    }

    @Test
    @DisplayName("background=true 는 직렬화·역직렬화 후에도 유지된다")
    void background_roundTrips() {
        Message sent = converter.toMessage(new SummonerMessage("KR", "p", 0L, true), new MessageProperties());

        SummonerMessage message = read(new String(sent.getBody(), StandardCharsets.UTF_8));

        assertThat(message.isBackground()).isTrue();
    }

    private SummonerMessage read(String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setInferredArgumentType(SummonerMessage.class);
        return (SummonerMessage) converter.fromMessage(new Message(json.getBytes(StandardCharsets.UTF_8), properties));
    }
}
