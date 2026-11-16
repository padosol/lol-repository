package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.application.port.MatchRepositoryPort;
import com.mmrtr.lol.infra.redis.service.MatchRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchIdPublisher {

    private final MatchRepositoryPort matchRepositoryPort;
    private final MatchRedisService matchRedisService;
    private final MessageSender messageSender;

    public int publishNewMatchIds(List<String> matchIds, String platformName) {
        List<String> existingIds = matchRepositoryPort.findExistingMatchIds(matchIds);
        Set<String> existingSet = new HashSet<>(existingIds);

        int sentCount = 0;
        for (String matchId : matchIds) {
            if (!existingSet.contains(matchId) && matchRedisService.tryMarkPending(matchId)) {
                messageSender.sendMessageByMatchId(matchId, platformName);
                sentCount++;
            }
        }

        log.info("matchId 발행: total={}, new={}", matchIds.size(), sentCount);
        return sentCount;
    }
}
