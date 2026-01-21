package com.mmrtr.lol.infra.persistence.summoner.repository;

import com.mmrtr.lol.infra.persistence.summoner.entity.SummonerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SummonerJpaRepository extends JpaRepository<SummonerEntity, String> {
}
