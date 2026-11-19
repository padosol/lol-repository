package com.mmrtr.lol.domain.summoner.application.usecase;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.support.riot.RiotCallPriority;

import java.util.Collection;
import java.util.Map;

public interface SummonerLookupUseCase {

    Map<String, Summoner> findAllByPuuidIn(Collection<String> puuids);

    Summoner getSummonerByPuuid(String regionType, String puuid, RiotCallPriority priority);
}
