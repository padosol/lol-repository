package com.mmrtr.lol.domain.league.repository;

import com.mmrtr.lol.domain.league.entity.LeagueSummonerHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeagueSummonerHistoryRepository extends JpaRepository<LeagueSummonerHistory, Long> {
}
