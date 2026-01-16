package com.mmrtr.lol.domain.league.repository;

import com.mmrtr.lol.domain.league.entity.LeagueSummonerHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeagueSummonerHistoryRepository extends JpaRepository<LeagueSummonerHistoryEntity, Long> {
}
