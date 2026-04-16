package com.mmrtr.lol.domain.summoner.application.port;

import com.mmrtr.lol.domain.summoner.domain.Summoner;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface SummonerRepositoryPort {

    void save(Summoner summoner);

    Optional<Summoner> findByPuuid(String puuid);

    Map<String, Summoner> findAllByPuuidIn(Collection<String> puuids);

    void updateLastRiotCallDate(String puuid);
}
