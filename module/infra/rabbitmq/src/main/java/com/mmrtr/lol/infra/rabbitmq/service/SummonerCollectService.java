package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.infra.riot.dto.summoner.SummonerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerCollectService {

    private static final long START_TIME_2026 = 1767225600L; // 2026-01-01T00:00:00Z

    private final SummonerDataCollector summonerDataCollector;
    private final MatchIdCollector matchIdCollector;
    private final MatchIdPublisher matchIdPublisher;
    private final Executor requestExecutor;

    public void collect(String puuid, Platform platform) {
        log.info("[수집 시작] puuid={}", puuid);

        SummonerDto summonerDto = summonerDataCollector.fetchSummoner(puuid, platform, requestExecutor);
        if (summonerDto == null) {
            log.error("소환사 정보 조회 실패. puuid={}", puuid);
            return;
        }

        Optional<Summoner> summonerOpt = summonerDataCollector
                .collectAndAssemble(puuid, platform, summonerDto, requestExecutor);
        if (summonerOpt.isEmpty()) {
            return;
        }

        Summoner summoner = summonerOpt.get();
        summoner.updateLastRiotCallDate();
        summonerDataCollector.save(summoner);

        List<String> allMatchIds = matchIdCollector
                .collectAll(puuid, platform.name(), START_TIME_2026, 0, requestExecutor);

        if (!allMatchIds.isEmpty()) {
            matchIdPublisher.publishNewMatchIds(allMatchIds, platform.name());
        }

        log.info("[수집 완료] puuid={}, matchIds={}", puuid, allMatchIds.size());
    }
}
