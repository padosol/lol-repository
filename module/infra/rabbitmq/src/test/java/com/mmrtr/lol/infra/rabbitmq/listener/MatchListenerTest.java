package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.rabbitmq.service.MatchDataProcessor;
import com.mmrtr.lol.infra.rabbitmq.service.MatchQueueRank;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.SimpleMessageConverter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchListenerTest {

    private final MatchDataProcessor processor = mock(MatchDataProcessor.class);
    private final Channel channel = mock(Channel.class);
    private final MatchListener listener = new MatchListener(new SimpleMessageConverter(), processor);

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
