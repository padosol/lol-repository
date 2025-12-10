package com.mmrtr.lol.rabbitmq.listener;

import com.mmrtr.lol.domain.match.entity.Match;
import com.mmrtr.lol.domain.match.service.MatchService;
import com.mmrtr.lol.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.rabbitmq.service.MessageSender;
import com.mmrtr.lol.riot.service.RiotApiServiceV2;
import com.mmrtr.lol.riot.type.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchFindListener {

    private final MessageSender messageSender;
    private final MatchService matchService;
    private final RiotApiServiceV2 riotApiServiceV2;

    @RabbitListener(queues = "renewal.match.find.queue", containerFactory = "findQueueSimpleRabbitListenerContainerFactory")
    public void findMatchIdsListener(@Payload SummonerRenewalMessage summonerRenewalMessage) {
        String puuid = summonerRenewalMessage.puuid();
        Platform platform = Platform.valueOfName(summonerRenewalMessage.platform());
        long revisionDate = summonerRenewalMessage.revisionDate();

        log.info("Starting renewal match ID search for puuid: {} on platform: {} with revisionDate: {}", puuid, platform, revisionDate);

        List<String> allFetchedMatchIds = new ArrayList<>();
        int offset = 20;
        int count = 100;

        while (true) {
            List<String> fetchedMatchIds = riotApiServiceV2.getMatchListByPuuid(puuid, platform, revisionDate, offset, count).join();

            if (fetchedMatchIds == null || fetchedMatchIds.isEmpty()) {
                log.info("No more match IDs found for puuid: {} at offset: {}. Ending search.", puuid, offset);
                break;
            }

            allFetchedMatchIds.addAll(fetchedMatchIds);

            if (fetchedMatchIds.size() < count) {
                log.info("Fewer than {} match IDs found ({}). Reached end of available matches for puuid: {}. Ending search.", count, fetchedMatchIds.size(), puuid);
                break;
            }

            offset += count;
        }

        if (allFetchedMatchIds.isEmpty()) {
            log.info("No match IDs fetched for puuid: {}. Nothing to process.", puuid);
            return;
        }

        List<Match> existingMatches = matchService.findAllMatch(allFetchedMatchIds);
        Set<String> existingMatchIds = existingMatches.stream().map(Match::getMatchId).collect(Collectors.toSet());

        List<String> newMatchIds = allFetchedMatchIds.stream()
                .filter(matchId -> !existingMatchIds.contains(matchId))
                .toList();

        log.info("Found {} total matches, {} of which are new. Sending to queue.", allFetchedMatchIds.size(), newMatchIds.size());

        for (String matchId : newMatchIds) {
            messageSender.sendMessageByMatchId(matchId, platform.name());
        }

        log.info("Completed renewal match ID search for puuid: {}.", puuid);
    }

}
