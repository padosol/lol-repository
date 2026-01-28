package com.mmrtr.lol.infra.persistence.match.repository;

import com.mmrtr.lol.infra.persistence.league.repository.MostChampionProjection;
import com.mmrtr.lol.infra.persistence.match.entity.MatchSummonerEntity;
import com.mmrtr.lol.infra.persistence.match.entity.id.MatchSummonerId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchSummonerJpaRepository extends JpaRepository<MatchSummonerEntity, MatchSummonerId> {

    @Query("""
            SELECT ms.puuid as puuid,
                   ms.championName as championName,
                   COUNT(ms) as playCount
            FROM MatchSummonerEntity ms
            WHERE ms.puuid IN :puuids
            GROUP BY ms.puuid, ms.championName
            ORDER BY ms.puuid, COUNT(ms) DESC
            """)
    List<MostChampionProjection> findMostChampionsByPuuids(@Param("puuids") List<String> puuids);
}
