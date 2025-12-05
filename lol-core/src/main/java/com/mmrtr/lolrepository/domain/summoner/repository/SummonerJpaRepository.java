package com.mmrtr.lolrepository.domain.summoner.repository;

import com.mmrtr.lolrepository.domain.summoner.entity.Summoner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SummonerJpaRepository extends JpaRepository<Summoner, String> {
}
