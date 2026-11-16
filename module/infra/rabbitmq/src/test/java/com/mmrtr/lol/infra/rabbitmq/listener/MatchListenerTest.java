package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.rabbitmq.service.MatchDataProcessor;
import com.mmrtr.lol.infra.rabbitmq.service.MatchQueueRank;
import com.mmrtr.lol.infra.rabbitmq.service.MessageSender;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.SimpleMessageConverter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MatchListenerTest {

    private final MatchDataProcessor processor = mock(MatchDataProcessor.class);
    private final Channel channel = mock(Channel.class);
    private final MessageSender messageSender = mock(MessageSender.class);
    private final List<Duration> sleeps = new ArrayList<>();
    private final MatchListener listener =
            new MatchListener(new SimpleMessageConverter(), processor, messageSender, sleeps::add);

    @Test
    @DisplayName("priority 2 메시지는 사용자 재시도 순위로 처리한다")
    void receiveMessage_priority2_processedAsUserRetry() throws IOException {
        // given
        when(processor.process(anyString(), anyString(), any())).thenReturn(MatchDataProcessor.Result.SUCCESS);
        when(channel.isOpen()).thenReturn(true);

        // when
        listener.receiveMessage(message(2), channel);

        // then
        verify(processor).process("KR_1", "KR", MatchQueueRank.USER_RETRY);
        verify(channel).basicAck(7L, false);
    }

    @Test
    @DisplayName("priority 가 없으면 백필 순위로 처리한다")
    void receiveMessage_noPriority_processedAsBackfill() throws IOException {
        // given
        when(processor.process(anyString(), anyString(), any())).thenReturn(MatchDataProcessor.Result.DUPLICATE);

        // when
        listener.receiveMessage(message(null), channel);

        // then
        verify(processor).process("KR_1", "KR", MatchQueueRank.BACKFILL);
    }

    @Test
    @DisplayName("처리가 예외를 던지면 밖으로 내보내지 않고 requeue 없이 nack 해 DLX 로 보낸다")
    void receiveMessage_processThrows_nacksWithoutRequeue() throws IOException {
        // given
        when(processor.process(anyString(), anyString(), any())).thenThrow(new IllegalStateException("db down"));
        when(channel.isOpen()).thenReturn(true);

        // when & then
        assertThatCode(() -> listener.receiveMessage(message(2), channel)).doesNotThrowAnyException();
        verify(channel).basicNack(7L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("허가가 거절되면 1초 쉰 뒤 requeue 로 nack 한다")
    void deniedSleepsOneSecondThenRequeues() throws IOException {
        // given
        when(processor.process(anyString(), anyString(), any())).thenReturn(MatchDataProcessor.Result.DENIED);
        when(channel.isOpen()).thenReturn(true);

        // when
        listener.receiveMessage(message(1), channel);

        // then
        assertThat(sleeps).containsExactly(Duration.ofMillis(1000));
        verify(channel).basicNack(7L, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("배치 큐가 가득 차면 거절처럼 1초 쉰 뒤 requeue 로 nack 한다")
    void queueFullSleepsOneSecondThenRequeues() throws IOException {
        // given
        when(processor.process(anyString(), anyString(), any())).thenReturn(MatchDataProcessor.Result.QUEUE_FULL);
        when(channel.isOpen()).thenReturn(true);

        // when
        listener.receiveMessage(message(2), channel);

        // then
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
        verify(channel).basicNack(7L, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("일시 오류의 첫 시도면 1초 쉬고 같은 id·region·순위에 시도 수 1 을 단 새 메시지를 보낸 뒤 원래 메시지를 ack 한다")
    void retryFirstAttempt_sleepsRepublishesWithNextAttemptThenAcks() throws IOException {
        // given
        when(processor.process(anyString(), anyString(), any())).thenReturn(MatchDataProcessor.Result.RETRY);
        when(channel.isOpen()).thenReturn(true);

        // when
        listener.receiveMessage(message(3), channel);

        // then
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
        verify(messageSender).sendMessageByMatchId("KR_1", "KR", MatchQueueRank.TIMELINE_LATER, 1);
        verify(channel).basicAck(7L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("일시 오류인데 다음 시도 수가 상한(2)에 닿으면 다시 보내지 않고 requeue 없이 nack 해 DLX 로 보낸다")
    void retryAtMaxAttempt_nacksToDeadLetter() throws IOException {
        // given
        when(processor.process(anyString(), anyString(), any())).thenReturn(MatchDataProcessor.Result.RETRY);
        when(channel.isOpen()).thenReturn(true);
        Message message = message(2);
        message.getMessageProperties().setHeader(MessageSender.ATTEMPT_HEADER, 1);

        // when
        listener.receiveMessage(message, channel);

        // then
        verify(channel).basicNack(7L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verifyNoInteractions(messageSender);
        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("다시 보내기가 실패하면 원래 메시지를 ack 하지 않고 requeue 없이 nack 해 DLX 로 보낸다")
    void retryRepublishFails_nacksToDeadLetter() throws IOException {
        // given
        when(processor.process(anyString(), anyString(), any())).thenReturn(MatchDataProcessor.Result.RETRY);
        when(channel.isOpen()).thenReturn(true);
        doThrow(new IllegalStateException("mq down"))
                .when(messageSender).sendMessageByMatchId(anyString(), anyString(), any(), anyInt());

        // when
        assertThatCode(() -> listener.receiveMessage(message(1), channel)).doesNotThrowAnyException();

        // then
        verify(channel).basicNack(7L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("FAILURE 결과는 requeue 없이 nack 한다")
    void receiveMessage_failure_nacksWithoutRequeue() throws IOException {
        // given
        when(processor.process(anyString(), anyString(), any())).thenReturn(MatchDataProcessor.Result.FAILURE);
        when(channel.isOpen()).thenReturn(true);

        // when
        listener.receiveMessage(message(1), channel);

        // then
        verify(channel).basicNack(7L, false, false);
    }

    private Message message(Integer priority) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
        props.setDeliveryTag(7L);
        props.setHeader("region", "KR");
        if (priority != null) {
            props.setPriority(priority);
        }
        return new Message("KR_1".getBytes(StandardCharsets.UTF_8), props);
    }
}
