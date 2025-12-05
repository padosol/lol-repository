package com.mmrtr.lolrepository.domain.champion.service;

import com.mmrtr.lolrepository.riot.core.api.RiotAPI;
import com.mmrtr.lolrepository.riot.dto.champion.ChampionInfo;
import com.mmrtr.lolrepository.riot.type.Platform;
import org.springframework.stereotype.Service;

@Service
public class ChampionRotateService {

    public ChampionInfo getChampionRotate(String region) {
        Platform platform = Platform.valueOfName(region);
        return RiotAPI.champion(platform).getChampionRotation();
    }

}
