package com.mmrtr.lol.infra.rabbitmq.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class RabbitQueueWaitTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AtomicLong now = new AtomicLong();
    private final RabbitQueueWait queueWait = new RabbitQueueWait(meterRegistry, now::get);

    @Test
    @DisplayName("발행할 때 발행 시각(ms)을 헤더에 찍는다")
    void stamp_putsPublishTimeHeader() {
        // given
        now.set(1_000L);

        // when
        Message stamped = queueWait.stamp().postProcessMessage(message(null));

        // then
        Object header = stamped.getMessageProperties().getHeader(RabbitQueueWait.HEADER);
        assertThat(header).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("받을 때 발행 시각부터 지금까지 기다린 시간을 받은 큐 이름으로 남긴다")
    void recorder_recordsWaitByConsumerQueue() {
        // given
        Message received = message(1_000L);
        received.getMessageProperties().setConsumerQueue("mmrtr.renewal.account");
        now.set(1_250L);

        // when
        Message result = queueWait.recorder().postProcessMessage(received);

        // then
        Timer timer = meterRegistry.find(RabbitQueueWait.METRIC).tag("queue", "mmrtr.renewal.account").timer();
        assertThat(result).isSameAs(received);
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(250.0);
    }

    @Test
    @DisplayName("발행 시각 헤더가 없는 메시지는 재지 않는다")
    void recorder_withoutHeader_recordsNothing() {
        // given
        Message received = message(null);
        received.getMessageProperties().setConsumerQueue("mmrtr.renewal.account");
        now.set(1_250L);

        // when
        queueWait.recorder().postProcessMessage(received);

        // then
        assertThat(meterRegistry.find(RabbitQueueWait.METRIC).timer()).isNull();
    }

    @Test
    @DisplayName("시계가 발행 시각보다 앞서 있어도 음수가 아니라 0으로 남긴다")
    void recorder_clockBehindPublishTime_recordsZero() {
        // given
        Message received = message(2_000L);
        received.getMessageProperties().setConsumerQueue("mmrtr.renewal.match");
        now.set(1_500L);

        // when
        queueWait.recorder().postProcessMessage(received);

        // then
        Timer timer = meterRegistry.find(RabbitQueueWait.METRIC).tag("queue", "mmrtr.renewal.match").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isZero();
    }

    private static Message message(Long publishedAt) {
        MessageProperties properties = new MessageProperties();
        if (publishedAt != null) {
            properties.setHeader(RabbitQueueWait.HEADER, publishedAt);
        }
        return new Message(new byte[0], properties);
    }
}
