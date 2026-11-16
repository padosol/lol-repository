package com.mmrtr.lol.infra.rabbitmq.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.function.LongSupplier;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 메시지가 큐에서 기다린 시간을 잰다. 발행할 때 {@value #HEADER} 헤더에 발행 시각(epoch ms)을 찍고, 리스너가 받기 직전에
 * 지금과의 차이를 {@value #METRIC}{queue=받은 큐} 로 남긴다. 갱신 단계마다 큐 대기와 처리 시간을 나눠 보기 위해서다.
 * AMQP timestamp 속성은 초 단위라 쓰지 않는다. 헤더가 없는 메시지(다른 발행자)는 재지 않는다.
 */
@Component
public class RabbitQueueWait {

    static final String HEADER = "x-lol-published-at";
    static final String METRIC = "lol.rabbit.queue_wait";

    private final MeterRegistry meterRegistry;
    private final LongSupplier clock;

    @Autowired
    public RabbitQueueWait(MeterRegistry meterRegistry) {
        this(meterRegistry, System::currentTimeMillis);
    }

    RabbitQueueWait(MeterRegistry meterRegistry, LongSupplier clock) {
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    /** 발행 템플릿의 before-publish 후처리기. 발행 시각을 헤더에 찍는다. */
    MessagePostProcessor stamp() {
        return message -> {
            message.getMessageProperties().setHeader(HEADER, clock.getAsLong());
            return message;
        };
    }

    /** 리스너 컨테이너의 after-receive 후처리기. 리스너를 부르기 직전에 기다린 시간을 남긴다. */
    MessagePostProcessor recorder() {
        return message -> {
            record(message.getMessageProperties());
            return message;
        };
    }

    private void record(MessageProperties properties) {
        Object publishedAt = properties.getHeader(HEADER);
        String queue = properties.getConsumerQueue();
        if (!(publishedAt instanceof Number publishedAtMillis) || queue == null) {
            return;
        }
        long waitedMillis = Math.max(0, clock.getAsLong() - publishedAtMillis.longValue());
        meterRegistry.timer(METRIC, "queue", queue).record(Duration.ofMillis(waitedMillis));
    }
}
