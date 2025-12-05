package com.mmrtr.lolrepository.domain.league.repository;

import com.mmrtr.lolrepository.domain.league.entity.LeagueSummoner;
import com.mmrtr.lolrepository.domain.league.entity.id.LeagueSummonerId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeagueSummonerJpaRepository extends JpaRepository<LeagueSummoner, Long>{
    Optional<LeagueSummoner> findByPuuidAndLeagueId(String puuid, String leagueId);

    Optional<LeagueSummoner> findAllByPuuidAndQueue(String puuid, String queue);
}
