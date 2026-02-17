package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.infra.persistence.match.entity.MatchEntity;
import com.mmrtr.lol.infra.persistence.match.service.MatchService;
import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.riot.dto.match.MatchDto;
import com.mmrtr.lol.infra.riot.dto.match_timeline.TimelineDto;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchDataFetcher {

    private static final int MATCH_FETCH_COUNT = 20;

    private final RiotApiService riotApiService;
    private final MatchService matchService;
    private final RabbitTemplate rabbitTemplate;

    public CompletableFuture<List<String>> fetchNewMatchIds(
            String puuid, Platform platform, long dbRevisionDateMillis, Executor executor) {

        CompletableFuture<List<String>> matchIdListFuture = riotApiService.getMatchListByPuuid(
                puuid, platform, dbRevisionDateMillis, 0, MATCH_FETCH_COUNT, executor);

        return matchIdListFuture.thenApply(matchIds -> {
            if (matchIds == null || matchIds.isEmpty()) {
                return List.of();
            }
            if (matchIds.size() == MATCH_FETCH_COUNT) {
                log.info("matchIds size is 20. send message for search more matchIds");
                rabbitTemplate.convertAndSend(
                        RabbitMqBinding.RENEWAL_MATCH_FIND.getExchange(),
                        RabbitMqBinding.RENEWAL_MATCH_FIND.getRoutingKey(),
                        new SummonerRenewalMessage(puuid, platform.getPlatformId(), dbRevisionDateMillis)
                );
            }

            List<MatchEntity> matchList = matchService.findAllMatch(matchIds);
            List<String> existMatchIds = matchList.stream().map(MatchEntity::getMatchId).toList();
            return matchIds.stream().filter(matchId -> !existMatchIds.contains(matchId)).toList();
        });
    }

    public CompletableFuture<List<MatchDto>> fetchMatchDetails(
            List<String> matchIds, Platform platform, Executor executor) {

        List<CompletableFuture<MatchDto>> matchAllOfFuture = matchIds.stream()
                .map(matchId -> riotApiService.getMatchById(matchId, platform, executor))
                .toList();

        CompletableFuture<Void> allOfFuture = CompletableFuture.allOf(matchAllOfFuture.toArray(new CompletableFuture[0]));

        return allOfFuture.thenApply(v -> matchAllOfFuture.stream()
                .map(CompletableFuture::join)
                .toList());
    }

    public CompletableFuture<List<TimelineDto>> fetchTimelines(
            List<String> matchIds, Platform platform, Executor executor) {

        List<CompletableFuture<TimelineDto>> timelineAllOfFuture = matchIds.stream()
                .map(matchId -> riotApiService.getTimelineById(matchId, platform, executor))
                .toList();

        CompletableFuture<Void> allOfFuture = CompletableFuture.allOf(timelineAllOfFuture.toArray(new CompletableFuture[0]));

        return allOfFuture.thenApply(v -> timelineAllOfFuture.stream()
                .map(CompletableFuture::join)
                .toList());
    }
}
