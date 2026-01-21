package com.mmrtr.lol.domain.league.repository;

import com.mmrtr.lol.domain.league.domain.LeagueSummoner;

import java.util.Optional;

public interface LeagueSummonerRepositoryPort {

    LeagueSummoner save(LeagueSummoner leagueSummoner);

    Optional<LeagueSummoner> findBy(String puuid, String leagueId);

    Optional<LeagueSummoner> findByPuuidAndQueue(String puuid, String queue);
}
