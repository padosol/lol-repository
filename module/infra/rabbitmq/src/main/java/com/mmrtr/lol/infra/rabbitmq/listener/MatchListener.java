package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.service.MatchDataProcessor;
import com.mmrtr.lol.infra.rabbitmq.service.MatchQueueRank;
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

@Slf4j
@Service
@RequiredArgsConstructor
@TraceLogging
public class MatchListener {

    private final MessageConverter messageConverter;
    private final MatchDataProcessor matchDataProcessor;

    /**
     * MANUAL ack 라 예외가 밖으로 나가면 컨테이너가 ack·nack 하지 않아 메시지가 un-acked 로 남고, prefetch 1 인 이 consumer 는
     * 브로커의 consumer_timeout 까지 멈춘다. 그래서 어떤 예외든 잡아 nack(requeue=false) 해 DLX 로 보낸다.
     */
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

        MatchDataProcessor.Result result;
        try {
            String platformName = message.getMessageProperties().getHeader("region");
            String matchId = messageConverter.fromMessage(message).toString();

            MatchQueueRank rank = MatchQueueRank.fromAmqpPriority(message.getMessageProperties().getPriority());

            result = matchDataProcessor.process(matchId, platformName, rank);
        } catch (Exception e) {
            log.warn("Failed to process match message, nacking to DLX. deliveryTag={}", deliveryTag, e);
            safeNack(channel, deliveryTag);
            return;
        }

        switch (result) {
            case DUPLICATE, SUCCESS -> safeAck(channel, deliveryTag);
            case QUEUE_FULL -> safeNackRequeue(channel, deliveryTag);
            case FAILURE -> safeNack(channel, deliveryTag);
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
