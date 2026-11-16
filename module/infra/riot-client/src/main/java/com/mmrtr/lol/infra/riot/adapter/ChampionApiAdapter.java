package com.mmrtr.lol.infra.riot.adapter;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.champion.domain.ChampionRotation;
import com.mmrtr.lol.domain.champion.application.port.ChampionApiPort;
import com.mmrtr.lol.infra.riot.dto.champion.ChampionInfo;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChampionApiAdapter implements ChampionApiPort {

    private final RiotApiService riotApiService;

    @Override
    public CompletableFuture<ChampionRotation> fetchChampionRotation(
            String platformName, RiotPermits permits, Executor executor) {
        Platform platform = Platform.valueOfName(platformName);

        return riotApiService.getChampionRotation(platform, permits.take(RiotMethod.CHAMPION_ROTATION), executor)
                .thenApply(this::toDomain);
    }

    private ChampionRotation toDomain(ChampionInfo championInfo) {
        return ChampionRotation.builder()
                .freeChampionIds(championInfo.getFreeChampionIds())
                .freeChampionIdsForNewPlayers(championInfo.getFreeChampionIdsForNewPlayers())
                .maxNewPlayerLevel(championInfo.getMaxNewPlayerLevel())
                .build();
    }
}
