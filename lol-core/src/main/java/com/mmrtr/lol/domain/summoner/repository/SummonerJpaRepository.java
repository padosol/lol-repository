package com.mmrtr.lol.domain.summoner.repository;

import com.mmrtr.lol.domain.summoner.entity.SummonerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SummonerJpaRepository extends JpaRepository<SummonerEntity, String> {
}
