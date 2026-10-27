package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.rabbitmq.service.MatchDataProcessor;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchListenerTest {

    private static final long DELIVERY_TAG = 7L;
    private static final String MATCH_ID = "KR_7000000000";
    private static final String PLATFORM = "KR";

    private final MessageConverter messageConverter = new Jackson2JsonMessageConverter();
    private MatchDataProcessor matchDataProcessor;
    private Channel channel;
    private MatchListener listener;

    @BeforeEach
    void setUp() {
        matchDataProcessor = mock(MatchDataProcessor.class);
        channel = mock(Channel.class);
        when(channel.isOpen()).thenReturn(true);
        listener = new MatchListener(messageConverter, matchDataProcessor);
    }

    @ParameterizedTest
    @EnumSource(value = MatchDataProcessor.Result.class, names = {"SUCCESS", "DUPLICATE"})
    @DisplayName("SUCCESS/DUPLICATE 이면 ack 한다")
    void receiveMessage_acks_whenProcessed(MatchDataProcessor.Result result) throws IOException {
        when(matchDataProcessor.process(MATCH_ID, PLATFORM)).thenReturn(result);

        listener.receiveMessage(matchIdMessage(), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("배치 큐가 가득 차면 requeue 로 nack 한다 (backpressure)")
    void receiveMessage_nackRequeue_whenQueueFull() throws IOException {
        when(matchDataProcessor.process(MATCH_ID, PLATFORM)).thenReturn(MatchDataProcessor.Result.QUEUE_FULL);

        listener.receiveMessage(matchIdMessage(), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
    }

    @Test
    @DisplayName("수집 실패면 requeue 없이 nack 해 DLQ 로 보낸다")
    void receiveMessage_nack_whenFailure() throws IOException {
        when(matchDataProcessor.process(MATCH_ID, PLATFORM)).thenReturn(MatchDataProcessor.Result.FAILURE);

        listener.receiveMessage(matchIdMessage(), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    @DisplayName("처리 중 예외가 나도 메시지를 unacked 로 남기지 않고 DLQ 로 보낸다")
    void receiveMessage_nack_whenProcessorThrows() throws IOException {
        when(matchDataProcessor.process(MATCH_ID, PLATFORM)).thenThrow(new IllegalStateException("redis down"));

        listener.receiveMessage(matchIdMessage(), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    /** MessageSender 와 같은 컨버터·헤더로 발행된 matchId 메시지. */
    private Message matchIdMessage() {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(DELIVERY_TAG);
        properties.setHeader("region", PLATFORM);
        return messageConverter.toMessage(MATCH_ID, properties);
    }
}
