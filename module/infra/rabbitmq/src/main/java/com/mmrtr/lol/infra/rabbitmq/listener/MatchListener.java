package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.service.MatchDataProcessor;
import com.mmrtr.lol.support.aop.TraceLogging;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * matchId 를 소비해 매치/타임라인을 수집한다.
 *
 * <p>MANUAL ack 이므로 컨테이너 재시도가 적용되지 않는다 — 처리 결과에 따라 직접 ack/nack 한다.
 * {@code QUEUE_FULL} 만 requeue 해 backpressure 를 걸고, 나머지 실패는 DLQ 로 보낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@TraceLogging
public class MatchListener {

    private final MessageConverter messageConverter;
    private final MatchDataProcessor matchDataProcessor;

    @RabbitListener(
            queues = RabbitMqBinding.Queue.MATCH_ID,
            containerFactory = "batchRabbitListenerContainerFactory",
            ackMode = "MANUAL"
    )
    public void receiveMessage(
            Message message,
            Channel channel
    ) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String matchId = null;

        try {
            String platformName = message.getMessageProperties().getHeader("region");
            matchId = messageConverter.fromMessage(message).toString();

            MatchDataProcessor.Result result = matchDataProcessor.process(matchId, platformName);

            switch (result) {
                case DUPLICATE, SUCCESS -> safeAck(channel, deliveryTag);
                case QUEUE_FULL -> safeNackRequeue(channel, deliveryTag);
                case FAILURE -> safeNack(channel, deliveryTag);
            }
        } catch (Exception e) {
            // 여기까지 온 예외는 payload 변환 실패 등 재시도해도 같은 결과인 경우가 대부분이다.
            // ack/nack 을 남기지 않으면 메시지가 unacked 로 묶이므로 반드시 DLQ 로 보낸다.
            log.error("매치 처리 중 예외 — DLQ 로 격리합니다. matchId={}", matchId, e);
            safeNack(channel, deliveryTag);
        }
    }

    private void safeAck(Channel channel, long deliveryTag) {
        try {
            if (channel.isOpen()) {
                channel.basicAck(deliveryTag, false);
            } else {
                log.warn("Channel is closed, cannot ack deliveryTag {}.", deliveryTag);
            }
        } catch (AlreadyClosedException e) {
            log.warn("Channel already closed during ack for deliveryTag {}: {}", deliveryTag, e.getMessage());
        } catch (IOException e) {
            log.warn("IOException during ack for deliveryTag {}: {}", deliveryTag, e.getMessage());
        }
    }

    private void safeNack(Channel channel, long deliveryTag) {
        try {
            if (channel.isOpen()) {
                channel.basicNack(deliveryTag, false, false);
            } else {
                log.warn("Channel is closed, cannot nack deliveryTag {}.", deliveryTag);
            }
        } catch (AlreadyClosedException e) {
            log.warn("Channel already closed during nack for deliveryTag {}: {}", deliveryTag, e.getMessage());
        } catch (IOException e) {
            log.warn("IOException during nack for deliveryTag {}: {}", deliveryTag, e.getMessage());
        }
    }

    private void safeNackRequeue(Channel channel, long deliveryTag) {
        try {
            if (channel.isOpen()) {
                channel.basicNack(deliveryTag, false, true);
            } else {
                log.warn("Channel is closed, cannot nack(requeue) deliveryTag {}.", deliveryTag);
            }
        } catch (AlreadyClosedException e) {
            log.warn("Channel already closed during nack(requeue) for deliveryTag {}: {}",
                    deliveryTag, e.getMessage());
        } catch (IOException e) {
            log.warn("IOException during nack(requeue) for deliveryTag {}: {}", deliveryTag, e.getMessage());
        }
    }
}
