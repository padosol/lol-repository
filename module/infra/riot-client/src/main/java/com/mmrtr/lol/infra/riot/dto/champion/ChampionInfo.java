package com.mmrtr.lol.infra.riot.dto.champion;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.mmrtr.lol.infra.riot.dto.error.ErrorDTO;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ChampionInfo extends ErrorDTO {

    // Riot champion-rotations 응답은 현재 필드명이 sr/newplayer 로 내려온다.
    // (구 스펙: freeChampionIds/freeChampionIdsForNewPlayers/maxNewPlayerLevel)
    // 구·신 포맷을 모두 수용하도록 @JsonAlias 로 매핑한다. 안 그러면 리스트가 null,
    // maxNewPlayerLevel 이 0 으로 조용히 비어 내려온다.
    private int maxNewPlayerLevel;

    @JsonAlias({"newplayer", "freeChampionIdsForNewPlayers"})
    private List<Integer> freeChampionIdsForNewPlayers;

    @JsonAlias({"sr", "freeChampionIds"})
    private List<Integer> freeChampionIds;

}
