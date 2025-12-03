package lol.mmrtr.lolrepository.domain.champion.service;

import lol.mmrtr.lolrepository.riot.core.api.RiotAPI;
import lol.mmrtr.lolrepository.riot.dto.champion.ChampionInfo;
import lol.mmrtr.lolrepository.riot.type.Platform;
import org.springframework.stereotype.Service;

@Service
public class ChampionRotateService {

    public ChampionInfo getChampionRotate(String region) {
        Platform platform = Platform.valueOfName(region);
        return RiotAPI.champion(platform).getChampionRotation();
    }

}
