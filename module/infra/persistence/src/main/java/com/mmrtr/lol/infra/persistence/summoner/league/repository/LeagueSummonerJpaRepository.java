package com.mmrtr.lol.infra.persistence.summoner.league.repository;

import com.mmrtr.lol.infra.persistence.summoner.league.entity.LeagueSummonerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface LeagueSummonerJpaRepository extends JpaRepository<LeagueSummonerEntity, Long> {
    Optional<LeagueSummonerEntity> findByPuuidAndLeagueId(String puuid, String leagueId);

    Optional<LeagueSummonerEntity> findAllByPuuidAndQueue(String puuid, String queue);

    List<LeagueSummonerEntity> findAllByPuuidInAndQueue(Collection<String> puuids, String queue);

    List<LeagueSummonerEntity> findAllByPuuidIn(Collection<String> puuids);

    List<LeagueSummonerEntity> findAllByPuuidOrderByIdAsc(String puuid);
}
