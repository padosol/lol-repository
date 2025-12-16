package com.mmrtr.lol.domain.league.repository;

import com.mmrtr.lol.domain.league.entity.League;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeagueJpaRepository extends JpaRepository<League, String> {
}
