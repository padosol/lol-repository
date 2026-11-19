package com.mmrtr.lol.domain.match.application.usecase;

import com.mmrtr.lol.domain.match.readmodel.MostChampionPlayCount;

import java.util.List;

public interface MostChampionQueryUseCase {

    List<MostChampionPlayCount> findMostChampionsByPuuids(List<String> puuids);
}
