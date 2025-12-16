package com.mmrtr.lol.domain.league.repository;

import com.mmrtr.lol.domain.league.entity.LeagueSummoner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LeagueSummonerJpaRepository extends JpaRepository<LeagueSummoner, Long>{
    Optional<LeagueSummoner> findByPuuidAndLeagueId(String puuid, String leagueId);

    Optional<LeagueSummoner> findAllByPuuidAndQueue(String puuid, String queue);
}
