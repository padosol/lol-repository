package com.mmrtr.lol.infra.persistence.league.repository;

import com.mmrtr.lol.domain.league.domain.League;
import com.mmrtr.lol.domain.league.repository.LeagueRepositoryPort;
import com.mmrtr.lol.infra.persistence.league.entity.LeagueEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LeagueRepositoryImpl implements LeagueRepositoryPort {

    private final LeagueJpaRepository jpaRepository;

    @Override
    public League save(League league) {
        LeagueEntity entity = LeagueEntity.fromDomain(league);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<League> findById(String leagueId) {
        return jpaRepository.findById(leagueId)
                .map(LeagueEntity::toDomain);
    }
}
