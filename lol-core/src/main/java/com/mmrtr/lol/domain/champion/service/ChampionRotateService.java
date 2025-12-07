package com.mmrtr.lol.domain.champion.service;

import com.mmrtr.lol.riot.core.api.RiotAPI;
import com.mmrtr.lol.riot.dto.champion.ChampionInfo;
import com.mmrtr.lol.riot.type.Platform;
import org.springframework.stereotype.Service;

@Service
public class ChampionRotateService{

    public ChampionInfo getChampionRotate(String region) {
        Platform platform = Platform.valueOfName(region);
        return RiotAPI.champion(platform).getChampionRotation();
    }

}
