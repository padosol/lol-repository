package com.mmrtr.lol.domain.champion.service;

import com.mmrtr.lol.riot.core.api.RiotAPI;
import com.mmrtr.lol.riot.dto.champion.ChampionInfo;
import com.mmrtr.lol.riot.service.RiotApiServiceV2;
import com.mmrtr.lol.riot.type.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChampionRotateService{

    private final RiotApiServiceV2 riotApiServiceV2;

    public ChampionInfo getChampionRotate(String region) {
        Platform platform = Platform.valueOfName(region);
        return riotApiServiceV2.getChampionRotation(platform).join();
    }

}
