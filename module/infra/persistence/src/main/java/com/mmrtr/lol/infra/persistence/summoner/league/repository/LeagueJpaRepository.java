package com.mmrtr.lol.infra.persistence.summoner.league.repository;

import com.mmrtr.lol.infra.persistence.summoner.league.entity.LeagueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeagueJpaRepository extends JpaRepository<LeagueEntity, String> {
}
