package com.mmrtr.lol.infra.rabbitmq.metrics;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 큐 적재량 / 컨슈머 수를 Micrometer 게이지로 노출한다.
 *
 * <p>큐에 소비자가 없어 메시지가 계속 쌓이는 상황은 브로커 디스크가 찰 때까지 아무 징후가 없다.
 * 이 지표가 있으면 {@code rabbitmq_queue_messages} 증가 추세와
 * {@code rabbitmq_queue_consumers == 0} 조건으로 Prometheus 알림을 걸 수 있다.
 *
 * <p>DLQ 도 함께 노출한다 — DLQ 는 정상 상태에서 항상 0 이므로 {@code > 0} 이면 곧바로 알림 대상이다.
 *
 * <p>{@code getQueueInfo} 는 브로커에 passive declare 를 보내므로 매 스크레이프마다 호출하지 않고
 * 주기적으로 갱신한 값을 게이지가 읽어가게 한다.
 */
@Slf4j
@Component
public class RabbitQueueMetrics {

    /** 조회 실패를 정상값 0 과 구분하기 위한 표식. */
    private static final long UNKNOWN = -1L;

    private static final List<String> MONITORED_QUEUES = List.of(
            RabbitMqBinding.Queue.SUMMONER,
            RabbitMqBinding.Queue.SUMMONER_DLX,
            RabbitMqBinding.Queue.MATCH_ID,
            RabbitMqBinding.Queue.MATCH_ID_DLX,
            RabbitMqBinding.Queue.RENEWAL_MATCH_FIND
    );

    private final RabbitAdmin rabbitAdmin;
    private final Map<String, AtomicLong> messageCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> consumerCounts = new ConcurrentHashMap<>();

    public RabbitQueueMetrics(RabbitAdmin rabbitAdmin, MeterRegistry meterRegistry) {
        this.rabbitAdmin = rabbitAdmin;

        for (String queue : MONITORED_QUEUES) {
            AtomicLong messages = new AtomicLong(UNKNOWN);
            AtomicLong consumers = new AtomicLong(UNKNOWN);
            messageCounts.put(queue, messages);
            consumerCounts.put(queue, consumers);

            Gauge.builder("rabbitmq.queue.messages", messages, AtomicLong::get)
                    .tag("queue", queue)
                    .description("큐에 적재된 메시지 수 (-1 = 조회 실패)")
                    .register(meterRegistry);
            Gauge.builder("rabbitmq.queue.consumers", consumers, AtomicLong::get)
                    .tag("queue", queue)
                    .description("큐에 연결된 컨슈머 수 (-1 = 조회 실패)")
                    .register(meterRegistry);
        }
    }

    @Scheduled(fixedRateString = "${lol.rabbitmq.metrics.refresh-interval-ms:15000}")
    public void refresh() {
        for (String queue : MONITORED_QUEUES) {
            try {
                QueueInformation info = rabbitAdmin.getQueueInfo(queue);
                if (info == null) {
                    log.debug("큐 정보를 찾을 수 없습니다. queue={}", queue);
                    markUnknown(queue);
                    continue;
                }
                messageCounts.get(queue).set(info.getMessageCount());
                consumerCounts.get(queue).set(info.getConsumerCount());
            } catch (Exception e) {
                log.warn("큐 지표 갱신 실패 queue={}", queue, e);
                markUnknown(queue);
            }
        }
    }

    private void markUnknown(String queue) {
        messageCounts.get(queue).set(UNKNOWN);
        consumerCounts.get(queue).set(UNKNOWN);
    }
}
