package com.mmrtr.lol.domain.league.repository;

import com.mmrtr.lol.domain.league.entity.LeagueSummonerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LeagueSummonerJpaRepository extends JpaRepository<LeagueSummonerEntity, Long>{
    Optional<LeagueSummonerEntity> findByPuuidAndLeagueId(String puuid, String leagueId);

    Optional<LeagueSummonerEntity> findAllByPuuidAndQueue(String puuid, String queue);
}
