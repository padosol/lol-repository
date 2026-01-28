package com.mmrtr.lol.infra.persistence.league.repository;

import com.mmrtr.lol.infra.persistence.league.entity.LeagueSummonerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeagueSummonerJpaRepository extends JpaRepository<LeagueSummonerEntity, Long> {
    Optional<LeagueSummonerEntity> findByPuuidAndLeagueId(String puuid, String leagueId);

    Optional<LeagueSummonerEntity> findAllByPuuidAndQueue(String puuid, String queue);

    @Query("""
            SELECT ls.puuid as puuid,
                   ls.queue as queue,
                   ls.tier as tier,
                   ls.rank as rank,
                   ls.leaguePoints as leaguePoints,
                   ls.wins as wins,
                   ls.losses as losses,
                   ls.absolutePoints as absolutePoints,
                   s.gameName as gameName,
                   s.tagLine as tagLine
            FROM LeagueSummonerEntity ls
            JOIN SummonerEntity s ON ls.puuid = s.puuid
            WHERE ls.queue = :queue
              AND ls.tier IN ('MASTER', 'GRANDMASTER', 'CHALLENGER')
            ORDER BY ls.absolutePoints DESC
            """)
    List<SummonerRankingProjection> findRankingByQueue(@Param("queue") String queue);
}
