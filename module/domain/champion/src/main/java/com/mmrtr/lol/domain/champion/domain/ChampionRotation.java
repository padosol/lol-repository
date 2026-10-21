package com.mmrtr.lol.domain.champion.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ChampionRotation {
    private List<Integer> freeChampionIds;
    private List<Integer> freeChampionIdsForNewPlayers;
    private int maxNewPlayerLevel;

    /** Riot 조회 실패 등으로 로테이션이 비었는지. 빈 값은 짧게만 캐싱한다. */
    public boolean isEmpty() {
        return freeChampionIds == null || freeChampionIds.isEmpty();
    }
}
