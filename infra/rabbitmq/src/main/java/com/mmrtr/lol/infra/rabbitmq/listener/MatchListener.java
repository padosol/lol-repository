package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.persistence.match.service.MatchService;
import com.mmrtr.lol.infra.riot.dto.match.MatchDto;
import com.mmrtr.lol.infra.riot.dto.match_timeline.TimelineDto;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import com.mmrtr.lol.common.type.Platform;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchListener {

    private final MessageConverter messageConverter;
    private final RiotApiService riotApiService;
    private final MatchService matchService;
    private final Executor riotApiExecutor;
    private final RRateLimiter globalApiRateLimiter;
    private final BlockingQueue<Pair<MatchDto, TimelineDto>> queue = new LinkedBlockingQueue<>();

    @Scheduled(fixedRate = 1000)
    public void queueDataInsert() {
        List<Pair<MatchDto, TimelineDto>> pairs = new ArrayList<>();
        int count = queue.drainTo(pairs);
        log.info("{}", count);
        if (count > 0) {
            log.debug("데이터 갯수: {}", count);

            List<MatchDto> matchDtos = new ArrayList<>();
            List<TimelineDto> timelineDtos = new ArrayList<>();
            for (Pair<MatchDto, TimelineDto> pair : pairs) {
                matchDtos.add(pair.getFirst());
                timelineDtos.add(pair.getSecond());
            }

            matchService.addAllMatch(matchDtos, timelineDtos);
        }
    }

    @RabbitListener(
            queues = "mmrtr.matchId",
            containerFactory = "batchRabbitListenerContainerFactory",
            ackMode = "MANUAL"
    )
    public void receiveMessage(
            Message message,
            Channel channel
    ) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        globalApiRateLimiter.acquire(1);

        Platform platform = Platform.valueOfName(message.getMessageProperties().getHeader("region"));
        String matchId = messageConverter.fromMessage(message).toString();

        CompletableFuture<MatchDto> matchDtoFuture = riotApiService.getMatchById(matchId, platform, riotApiExecutor);
        CompletableFuture<TimelineDto> timelineDtoFuture = riotApiService.getTimelineById(matchId, platform, riotApiExecutor);

        CompletableFuture<Pair<MatchDto, TimelineDto>> completableFuture = CompletableFuture.allOf(matchDtoFuture, timelineDtoFuture)
                .thenApply(v -> Pair.of(matchDtoFuture.join(), timelineDtoFuture.join()))
                .exceptionally(throwable -> {
                    // DLQ 에 matchId를 넣는다.

                    log.warn("Failed to fetch data for matchId {}: {}", matchId, throwable.getMessage());
                    return null;
                });

        Pair<MatchDto, TimelineDto> dtoPair = completableFuture.join();
        queue.add(dtoPair);
        channel.basicAck(deliveryTag, false);
    }

}
