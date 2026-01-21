package com.mmrtr.lol.infra.persistence.summoner.repository;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.repository.SummonerRepositoryPort;
import com.mmrtr.lol.infra.persistence.summoner.entity.SummonerEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SummonerRepositoryImpl implements SummonerRepositoryPort {

    private final SummonerJpaRepository jpaRepository;

    @Override
    public void save(Summoner summoner) {
        SummonerEntity entity = SummonerEntity.fromDomain(summoner);
        entity.initRevisionDate();
        jpaRepository.save(entity);
    }

    @Override
    public Optional<Summoner> findByPuuid(String puuid) {
        return jpaRepository.findById(puuid)
                .map(SummonerEntity::toDomain);
    }
}
