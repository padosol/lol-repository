package com.mmrtr.lol.infra.persistence.league.repository;

import com.mmrtr.lol.infra.persistence.league.entity.LeagueSummonerHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeagueSummonerHistoryJpaRepository extends JpaRepository<LeagueSummonerHistoryEntity, Long> {
}
