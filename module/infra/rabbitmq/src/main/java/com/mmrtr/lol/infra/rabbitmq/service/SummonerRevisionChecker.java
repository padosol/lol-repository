package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.application.port.SummonerRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummonerRevisionChecker {

    private final SummonerRepositoryPort summonerRepositoryPort;

    public RevisionCheckResult check(String puuid, long riotRevisionDateMillis) {
        Optional<Summoner> existingSummoner = summonerRepositoryPort.findByPuuid(puuid);

        long dbRevisionDateSeconds = existingSummoner
                .map(s -> s.getRevisionInfo().revisionDate()
                        .atZone(ZoneId.systemDefault()).toInstant().getEpochSecond())
                .orElse(0L);

        if (existingSummoner.isPresent()) {
            LocalDateTime riotRevisionDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(riotRevisionDateMillis), ZoneId.systemDefault());
            log.info("revision 비교 - DB: {}, RIOT: {}", existingSummoner.get().getRevisionInfo().revisionDate(), riotRevisionDate);
            if (existingSummoner.get().getRevisionInfo().revisionDate().equals(riotRevisionDate)) {
                log.info("revisionDate is same. No need to update. revision: {}", riotRevisionDate);
                return new RevisionCheckResult(false, dbRevisionDateSeconds);
            }
        }

        return new RevisionCheckResult(true, dbRevisionDateSeconds);
    }

    public record RevisionCheckResult(boolean needsRenewal, long dbRevisionDateSeconds) {
    }
}
