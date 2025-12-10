package com.mmrtr.lol.rabbitmq.listener;

import com.mmrtr.lol.domain.match.service.MatchService;
import com.mmrtr.lol.riot.dto.match.MatchDto;
import com.mmrtr.lol.riot.dto.match_timeline.TimelineDto;
import com.mmrtr.lol.riot.service.RiotApiServiceV2;
import com.mmrtr.lol.riot.type.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchListener {

    private final MessageConverter messageConverter;
    private final RiotApiServiceV2 riotApiServiceV2;
    private final MatchService matchService;

    @Scheduled(fixedRate = 1000)
    @RabbitListener(queues = "mmrtr.matchId", containerFactory = "batchRabbitListenerContainerFactory")
    public void receiveMessage(
            List<Message> messages
    ) throws InterruptedException {
        if (messages == null || messages.isEmpty()) {
            log.info("Received an empty list of match IDs. No action taken.");
            return;
        }
        long start = System.currentTimeMillis();
        log.info("V2 Start processing {} match IDs.", messages.size());

        List<CompletableFuture<Pair<MatchDto, TimelineDto>>> futures = messages.stream()
                .map(message -> {

                    Platform platform = Platform.valueOfName(message.getMessageProperties().getHeader("region"));
                    String matchId = messageConverter.fromMessage(message).toString();

                    CompletableFuture<MatchDto> matchDtoFuture = riotApiServiceV2.getMatchById(matchId, platform);
                    CompletableFuture<TimelineDto> timelineDtoFuture = riotApiServiceV2.getTimelineById(matchId, platform);

                    return CompletableFuture.allOf(matchDtoFuture, timelineDtoFuture)
                            .thenApply(v -> Pair.of(matchDtoFuture.join(), timelineDtoFuture.join()))
                            .exceptionally(throwable -> {
                                log.error("Failed to fetch data for matchId {}: {}", matchId, throwable.getMessage());
                                return null;
                            });
                })
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenAccept(v -> {
                    List<Pair<MatchDto, TimelineDto>> results = futures.stream()
                            .map(CompletableFuture::join)
                            .filter(Objects::nonNull)
                            .toList();

                    if (results.isEmpty()) {
                        log.warn("No match data could be fetched for the batch starting with {}.", messages.get(0));
                        return;
                    }
                    List<MatchDto> matchDtos = results.stream().map(Pair::getFirst).toList();
                    List<TimelineDto> timelineDtos = results.stream().map(Pair::getSecond).toList();
                    matchService.addAllMatch(matchDtos, timelineDtos);
                    long end = System.currentTimeMillis();
                    log.info("V2 End processing. Fetched {} matches. Total time: {} ms", results.size(), end - start);
                });
    }
}
