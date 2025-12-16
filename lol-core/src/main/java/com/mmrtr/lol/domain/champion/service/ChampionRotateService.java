package com.mmrtr.lol.domain.champion.service;

import com.mmrtr.lol.riot.dto.champion.ChampionInfo;
import com.mmrtr.lol.riot.service.RiotApiService;
import com.mmrtr.lol.riot.type.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
public class ChampionRotateService{

    private final RiotApiService riotApiService;
    private final Executor requestExecutor;

    public ChampionInfo getChampionRotate(String region) {
        Platform platform = Platform.valueOfName(region);
        return riotApiService.getChampionRotation(platform, requestExecutor).join();
    }

}
