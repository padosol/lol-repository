package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.service.MatchDataProcessor;
import com.mmrtr.lol.infra.rabbitmq.service.MatchQueueRank;
import com.mmrtr.lol.infra.rabbitmq.service.MessageSender;
import com.mmrtr.lol.support.aop.TraceLogging;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
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

    /**
     * 허가가 거절됐거나 배치 큐가 가득 찬 메시지, 일시 오류로 다시 넣는 메시지를 넣기 전에 쉬는 시간.
     * 쉬지 않으면 같은 메시지가 쉬지 않고 돈다.
     */
    static final Duration REQUEUE_DELAY = Duration.ofSeconds(1);

    /** 일시 오류의 시도 수 상한. 다음 시도 수가 이 값에 닿으면 다시 넣지 않고 DLX 로 보낸다. */
    static final int MAX_ATTEMPT = 2;

    private final MessageConverter messageConverter;
    private final MatchDataProcessor matchDataProcessor;
    private final MessageSender messageSender;
    private final Sleeper sleeper;

    @Autowired
    public MatchListener(MessageConverter messageConverter, MatchDataProcessor matchDataProcessor,
                         MessageSender messageSender) {
        this(messageConverter, matchDataProcessor, messageSender, MatchListener::sleep);
    }

    MatchListener(MessageConverter messageConverter, MatchDataProcessor matchDataProcessor,
                  MessageSender messageSender, Sleeper sleeper) {
        this.messageConverter = messageConverter;
        this.matchDataProcessor = matchDataProcessor;
        this.messageSender = messageSender;
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

        String platformName;
        String matchId;
        MatchQueueRank rank;
        MatchDataProcessor.Result result;
        try {
            platformName = message.getMessageProperties().getHeader("region");
            matchId = messageConverter.fromMessage(message).toString();

            rank = MatchQueueRank.fromAmqpPriority(message.getMessageProperties().getPriority());

            result = matchDataProcessor.process(matchId, platformName, rank);
        } catch (Exception e) {
            log.warn("Failed to process match message, nacking to DLX. deliveryTag={}", deliveryTag, e);
            safeNack(channel, deliveryTag);
            return;
        }

        switch (result) {
            case DUPLICATE, SUCCESS -> safeAck(channel, deliveryTag);
            case QUEUE_FULL, DENIED -> {
                sleeper.sleep(REQUEUE_DELAY);
                safeNackRequeue(channel, deliveryTag);
            }
            case RETRY -> retryOrDeadLetter(message, channel, matchId, platformName, rank);
            case FAILURE -> safeNack(channel, deliveryTag);
        }
    }

    /**
     * 일시 오류. 다음 시도 수가 {@link #MAX_ATTEMPT} 에 닿지 않았으면 잠시 쉬고 같은 id·region·순위에 다음 시도 수를 단
     * 새 메시지를 보낸 뒤 원래 메시지를 ack 한다. 닿았거나 보내기가 실패하면 requeue 없이 nack 해 DLX 로 보낸다.
     * 시도 수는 헤더로 센다 — redelivered 는 거절된 메시지를 다시 넣을 때도 켜지므로 쓰지 않는다.
     */
    private void retryOrDeadLetter(Message message, Channel channel, String matchId, String platformName,
                                   MatchQueueRank rank) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        int nextAttempt = attemptOf(message.getMessageProperties()) + 1;
        if (nextAttempt >= MAX_ATTEMPT) {
            log.warn("Transient failure retries exhausted, nacking to DLX. matchId={}, attempt={}", matchId, nextAttempt);
            safeNack(channel, deliveryTag);
            return;
        }
        sleeper.sleep(REQUEUE_DELAY);
        try {
            messageSender.sendMessageByMatchId(matchId, platformName, rank, nextAttempt);
        } catch (RuntimeException e) {
            log.warn("Failed to republish match message for retry, nacking to DLX. matchId={}", matchId, e);
            safeNack(channel, deliveryTag);
            return;
        }
        safeAck(channel, deliveryTag);
    }

    /** {@link MessageSender#ATTEMPT_HEADER} 의 정수 값. 없거나 읽을 수 없으면 0. */
    static int attemptOf(MessageProperties properties) {
        Object value = properties.getHeader(MessageSender.ATTEMPT_HEADER);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
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
