package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.repository.SummonerRepositoryPort;
import com.mmrtr.lol.infra.riot.dto.summoner.SummonerDto;
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

    public RevisionCheckResult check(String puuid, SummonerDto summonerDto) {
        Optional<Summoner> existingSummoner = summonerRepositoryPort.findByPuuid(puuid);

        long dbRevisionDateMillis = existingSummoner
                .map(s -> s.getRevisionInfo().revisionDate()
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .orElse(0L);

        if (existingSummoner.isPresent()) {
            LocalDateTime riotRevisionDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(summonerDto.getRevisionDate()), ZoneId.systemDefault());
            if (existingSummoner.get().getRevisionInfo().revisionDate().equals(riotRevisionDate)) {
                log.info("revisionDate is same. No need to update.");
                return new RevisionCheckResult(false, dbRevisionDateMillis);
            }
        }

        return new RevisionCheckResult(true, dbRevisionDateMillis);
    }

    public record RevisionCheckResult(boolean needsRenewal, long dbRevisionDateMillis) {
    }
}
