package com.mmrtr.lol.domain.champion.service;

import com.mmrtr.lol.riot.dto.champion.ChampionInfo;
import com.mmrtr.lol.riot.service.RiotApiService;
import com.mmrtr.lol.riot.type.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChampionRotateService{

    private final RiotApiService riotApiService;

    public ChampionInfo getChampionRotate(String region) {
        Platform platform = Platform.valueOfName(region);
        return riotApiService.getChampionRotation(platform).join();
    }

}
