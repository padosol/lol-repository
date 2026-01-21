package com.mmrtr.lol.domain.summoner.repository;

import com.mmrtr.lol.domain.summoner.domain.Summoner;

import java.util.Optional;

public interface SummonerRepositoryPort {

    void save(Summoner summoner);

    Optional<Summoner> findByPuuid(String puuid);
}
