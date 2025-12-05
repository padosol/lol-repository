package com.mmrtr.lolrepository.domain.league.repository;

import com.mmrtr.lolrepository.domain.league.entity.LeagueSummonerHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeagueSummonerHistoryRepository extends JpaRepository<LeagueSummonerHistory, Long> {
}
