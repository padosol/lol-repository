package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.service.MatchBatchProcessor;
import com.mmrtr.lol.infra.redis.service.MatchRedisService;
import com.mmrtr.lol.support.aop.TraceLogging;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
@TraceLogging
public class MatchListener {

    private final MessageConverter messageConverter;
    private final MatchApiPort matchApiPort;
    private final MatchRedisService matchRedisService;
    private final MatchBatchProcessor matchBatchProcessor;
    private final Executor riotApiExecutor;
    private final RRateLimiter globalApiRateLimiter;

    @RabbitListener(
            queues = RabbitMqBinding.Queue.MATCH_ID,
            containerFactory = "batchRabbitListenerContainerFactory",
            ackMode = "MANUAL"
    )
    public void receiveMessage(
            Message message,
            Channel channel
    ) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        String platformName = message.getMessageProperties().getHeader("region");
        String matchId = messageConverter.fromMessage(message).toString();

        if (!matchRedisService.tryMarkProcessing(matchId)) {
            log.debug("Skipping duplicate matchId: {}", matchId);
            safeAck(channel, deliveryTag);
            return;
        }

        globalApiRateLimiter.acquire(1);

        CompletableFuture<MatchDto> matchDtoFuture = matchApiPort.fetchMatchById(matchId, platformName, riotApiExecutor);
        CompletableFuture<TimelineDto> timelineDtoFuture = matchApiPort.fetchTimelineById(matchId, platformName, riotApiExecutor);

        CompletableFuture<Pair<MatchDto, TimelineDto>> completableFuture =
                CompletableFuture.allOf(matchDtoFuture, timelineDtoFuture)
                        .thenApply(v -> Pair.of(matchDtoFuture.join(), timelineDtoFuture.join()))
                        .exceptionally(throwable -> {
                            log.warn("Failed to fetch data for matchId {}: {}", matchId, throwable.getMessage());
                            return null;
                        });

        Pair<MatchDto, TimelineDto> dtoPair;
        try {
            dtoPair = completableFuture.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("Timeout fetching match data for matchId {}", matchId);
            safeNack(channel, deliveryTag);
            return;
        } catch (InterruptedException e) {
            log.warn("Interrupted while fetching match data for matchId {}", matchId);
            Thread.currentThread().interrupt();
            safeNack(channel, deliveryTag);
            return;
        } catch (ExecutionException e) {
            log.warn("Execution error fetching match data for matchId {}: {}", matchId, e.getMessage());
            safeNack(channel, deliveryTag);
            return;
        }

        if (dtoPair == null) {
            safeNack(channel, deliveryTag);
            return;
        }
        matchBatchProcessor.add(dtoPair);
        safeAck(channel, deliveryTag);
    }

    private void safeAck(Channel channel, long deliveryTag) {
        try {
            if (channel.isOpen()) {
                channel.basicAck(deliveryTag, false);
            } else {
                log.warn("Channel is closed, cannot ack deliveryTag {}. Message will be requeued by broker.", deliveryTag);
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
                log.warn("Channel is closed, cannot nack deliveryTag {}. Message will be requeued by broker.", deliveryTag);
            }
        } catch (AlreadyClosedException e) {
            log.warn("Channel already closed during nack for deliveryTag {}: {}", deliveryTag, e.getMessage());
        } catch (IOException e) {
            log.warn("IOException during nack for deliveryTag {}: {}", deliveryTag, e.getMessage());
        }
    }
}
