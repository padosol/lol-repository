package com.mmrtr.lol.infra.rabbitmq.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MessageSenderTest {

    @Test
    @DisplayName("새 우선순위 exchange 로 순위의 AMQP priority 와 region 헤더를 실어 보낸다")
    void sendMessageByMatchId_setsPriorityAndRegion() {
        // given
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        MessageSender sender = new MessageSender(rabbitTemplate);
        ArgumentCaptor<MessagePostProcessor> captor = ArgumentCaptor.forClass(MessagePostProcessor.class);

        // when
        sender.sendMessageByMatchId("KR_1", "KR", MatchQueueRank.USER_RETRY);

        // then
        verify(rabbitTemplate).convertAndSend(eq("mmrtr.matchId.priority.exchange"), eq("mmrtr.routingkey.matchId"),
                eq((Object) "KR_1"), captor.capture());
        Message message = new Message(new byte[0], new MessageProperties());
        captor.getValue().postProcessMessage(message);
        assertThat(message.getMessageProperties().getPriority()).isEqualTo(2);
        assertThat((String) message.getMessageProperties().getHeader("region")).isEqualTo("KR");
        assertThat((Integer) message.getMessageProperties().getHeader("x-attempt")).isZero();
    }

    @Test
    @DisplayName("시도 수를 주면 x-attempt 헤더에 실어 같은 순위로 보낸다")
    void sendMessageByMatchId_withAttempt_setsAttemptHeader() {
        // given
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        MessageSender sender = new MessageSender(rabbitTemplate);
        ArgumentCaptor<MessagePostProcessor> captor = ArgumentCaptor.forClass(MessagePostProcessor.class);

        // when
        sender.sendMessageByMatchId("KR_1", "KR", MatchQueueRank.TIMELINE_LATER, 1);

        // then
        verify(rabbitTemplate).convertAndSend(eq("mmrtr.matchId.priority.exchange"), eq("mmrtr.routingkey.matchId"),
                eq((Object) "KR_1"), captor.capture());
        Message message = new Message(new byte[0], new MessageProperties());
        captor.getValue().postProcessMessage(message);
        assertThat(message.getMessageProperties().getPriority()).isEqualTo(3);
        assertThat((String) message.getMessageProperties().getHeader("region")).isEqualTo("KR");
        assertThat((Integer) message.getMessageProperties().getHeader(MessageSender.ATTEMPT_HEADER)).isEqualTo(1);
    }
}
