package com.mmrtr.lol.infra.persistence.summoner.repository;

import com.mmrtr.lol.infra.persistence.summoner.entity.SummonerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SummonerJpaRepository extends JpaRepository<SummonerEntity, String> {

    List<SummonerEntity> findAllByPuuidIn(Collection<String> puuids);
}
