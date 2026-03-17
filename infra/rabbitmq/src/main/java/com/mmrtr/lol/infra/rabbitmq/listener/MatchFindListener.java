package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.domain.match.repository.MatchRepositoryPort;
import com.mmrtr.lol.domain.match.service.port.MatchApiPort;
import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.rabbitmq.service.MessageSender;
import com.mmrtr.lol.common.type.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchFindListener {

    private final MatchRepositoryPort matchRepositoryPort;
    private final MessageSender messageSender;
    private final MatchApiPort matchApiPort;
    private final Executor matchFindExecutor;


    @RabbitListener(queues = RabbitMqBinding.Queue.RENEWAL_MATCH_FIND, containerFactory = "findQueueSimpleRabbitListenerContainerFactory")
    public void findMatchIdsListener(@Payload SummonerRenewalMessage summonerRenewalMessage) {
        String puuid = summonerRenewalMessage.puuid();
        Platform platform = Platform.valueOfName(summonerRenewalMessage.platform());
        long revisionDate = summonerRenewalMessage.revisionDate();

        log.info("Starting renewal match ID search for puuid: {} on platform: {} with revisionDate: {}",
                puuid, platform, revisionDate);
        log.debug("LocalDateTime: {}", LocalDateTime.ofInstant(Instant.ofEpochSecond(revisionDate), ZoneId.systemDefault()));

        List<String> allFetchedMatchIds = new ArrayList<>();
        int offset = 20;
        int count = 100;

        int retry = 0;
        boolean hasMoreMatches = true;
        while (retry < 10 && hasMoreMatches) {
            List<String> fetchedMatchIds = matchApiPort
                    .fetchMatchIdsByPuuid(puuid, platform.name(), revisionDate, offset, count, matchFindExecutor).join();

            if (fetchedMatchIds == null || fetchedMatchIds.isEmpty()) {
                log.debug("No more match IDs found for puuid: {} at offset: {}. Ending search.", puuid, offset);
                hasMoreMatches = false;
            } else {
                allFetchedMatchIds.addAll(fetchedMatchIds);

                if (fetchedMatchIds.size() < count) {
                    log.debug("Fewer than {} match IDs found ({}). Reached end of available matches for puuid: {}. Ending search.", count, fetchedMatchIds.size(), puuid);
                    hasMoreMatches = false;
                } else {
                    offset += count;
                    retry += 1;
                }
            }
        }

        if (allFetchedMatchIds.isEmpty()) {
            log.info("No match IDs fetched for puuid: {}. Nothing to process.", puuid);
            return;
        }

        List<String> existingMatchIds = matchRepositoryPort.findExistingMatchIds(allFetchedMatchIds);
        Set<String> existingMatchIdSet = new HashSet<>(existingMatchIds);

        List<String> newMatchIds = allFetchedMatchIds.stream()
                .filter(matchId -> !existingMatchIdSet.contains(matchId))
                .toList();

        log.info("Found {} total matches, {} of which are new. Sending to queue.", allFetchedMatchIds.size(), newMatchIds.size());

        for (String matchId : newMatchIds) {
            messageSender.sendMessageByMatchId(matchId, platform.name());
        }

        log.info("Completed renewal match ID search for puuid: {}.", puuid);
    }

}
