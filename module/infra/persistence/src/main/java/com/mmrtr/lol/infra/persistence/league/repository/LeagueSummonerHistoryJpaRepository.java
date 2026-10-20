package com.mmrtr.lol.infra.persistence.league.repository;

import com.mmrtr.lol.infra.persistence.league.entity.LeagueSummonerHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeagueSummonerHistoryJpaRepository extends JpaRepository<LeagueSummonerHistoryEntity, Long> {

    /** idx_puuid_queue 를 탄다 (league_summoner_id 에는 인덱스가 없다). */
    List<LeagueSummonerHistoryEntity> findAllByPuuidOrderByCreatedAtDescIdDesc(String puuid);
}
