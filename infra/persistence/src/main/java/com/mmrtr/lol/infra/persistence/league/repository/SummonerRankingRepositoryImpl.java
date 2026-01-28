package com.mmrtr.lol.infra.persistence.league.repository;

import com.mmrtr.lol.domain.league.domain.SummonerRanking;
import com.mmrtr.lol.domain.league.repository.SummonerRankingRepositoryPort;
import com.mmrtr.lol.infra.persistence.league.entity.SummonerRankingEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SummonerRankingRepositoryImpl implements SummonerRankingRepositoryPort {

    private final SummonerRankingJpaRepository jpaRepository;

    @Override
    public List<SummonerRanking> saveAll(List<SummonerRanking> rankings) {
        List<SummonerRankingEntity> entities = rankings.stream()
                .map(SummonerRankingEntity::fromDomain)
                .toList();
        return jpaRepository.saveAll(entities).stream()
                .map(SummonerRankingEntity::toDomain)
                .toList();
    }

    @Override
    public Map<String, Integer> findPreviousRanksByQueue(String queue) {
        List<SummonerRankingEntity> latestRankings = jpaRepository.findLatestByQueue(queue);
        return latestRankings.stream()
                .collect(Collectors.toMap(
                        SummonerRankingEntity::getPuuid,
                        SummonerRankingEntity::getCurrentRank,
                        (existing, replacement) -> existing
                ));
    }
}
