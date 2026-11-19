package com.mmrtr.lol.domain.match.application.port;

import com.mmrtr.lol.domain.match.readmodel.MostChampionPlayCount;

import java.util.List;

public interface MostChampionQueryPort {

    List<MostChampionPlayCount> findMostChampionsByPuuids(List<String> puuids);
}
