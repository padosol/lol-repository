package com.mmrtr.lolrepository.domain.league.repository;

import com.mmrtr.lolrepository.domain.league.entity.League;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeagueJpaRepository extends JpaRepository<League, String> {
}
