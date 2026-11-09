package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.service.MatchDataProcessor;
import com.mmrtr.lol.infra.rabbitmq.service.MatchQueueRank;
import com.mmrtr.lol.support.aop.TraceLogging;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Service
@TraceLogging
public class MatchListener {

    /** 허가가 거절된 메시지를 다시 넣기 전에 쉬는 시간. 쉬지 않으면 같은 메시지가 쉬지 않고 돈다. */
    static final Duration DENIED_REQUEUE_DELAY = Duration.ofSeconds(1);

    private final MessageConverter messageConverter;
    private final MatchDataProcessor matchDataProcessor;
    private final Sleeper sleeper;

    @Autowired
    public MatchListener(MessageConverter messageConverter, MatchDataProcessor matchDataProcessor) {
        this(messageConverter, matchDataProcessor, MatchListener::sleep);
    }

    MatchListener(MessageConverter messageConverter, MatchDataProcessor matchDataProcessor, Sleeper sleeper) {
        this.messageConverter = messageConverter;
        this.matchDataProcessor = matchDataProcessor;
        this.sleeper = sleeper;
    }

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
            case DENIED -> {
                sleeper.sleep(DENIED_REQUEUE_DELAY);
                safeNackRequeue(channel, deliveryTag);
            }
            case FAILURE -> safeNack(channel, deliveryTag);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration);
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
