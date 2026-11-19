package com.mmrtr.lol.infra.persistence.match.repository;

import com.mmrtr.lol.domain.match.application.port.MostChampionQueryPort;
import com.mmrtr.lol.domain.match.readmodel.MostChampionPlayCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MostChampionQueryPersistenceAdapter implements MostChampionQueryPort {

    private final MatchSummonerJpaRepository matchSummonerJpaRepository;

    @Override
    public List<MostChampionPlayCount> findMostChampionsByPuuids(List<String> puuids) {
        return matchSummonerJpaRepository.findMostChampionsByPuuids(puuids).stream()
                .map(p -> new MostChampionPlayCount(p.getPuuid(), p.getChampionName(), p.getPlayCount()))
                .toList();
    }
}
