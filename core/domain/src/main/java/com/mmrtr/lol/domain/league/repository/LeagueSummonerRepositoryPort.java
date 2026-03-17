package com.mmrtr.lol.domain.league.repository;

import com.mmrtr.lol.domain.league.domain.LeagueSummoner;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LeagueSummonerRepositoryPort {

    LeagueSummoner save(LeagueSummoner leagueSummoner);

    Optional<LeagueSummoner> findBy(String puuid, String leagueId);

    Optional<LeagueSummoner> findByPuuidAndQueue(String puuid, String queue);

    List<LeagueSummoner> findAllByPuuidsAndQueue(Set<String> puuids, String queue);
}
