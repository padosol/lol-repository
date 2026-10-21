package com.mmrtr.lol.infra.persistence.match.repository;

import com.mmrtr.lol.infra.persistence.league.repository.MostChampionProjection;
import com.mmrtr.lol.infra.persistence.match.entity.MatchParticipantEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchSummonerJpaRepository extends JpaRepository<MatchParticipantEntity, Long> {

    @Query("""
            SELECT ms.puuid as puuid,
                   ms.championName as championName,
                   COUNT(ms) as playCount
            FROM MatchParticipantEntity ms
            WHERE ms.puuid IN :puuids
            GROUP BY ms.puuid, ms.championName
            ORDER BY ms.puuid, COUNT(ms) DESC
            """)
    List<MostChampionProjection> findMostChampionsByPuuids(@Param("puuids") List<String> puuids);

    /**
     * 큐별 챔피언 판 수·승패. lol-server {@code findAllRankedMatchSummonerByPuuidAndSeason} 과 같이
     * challenges 행이 있는 게임만 센다 (소환사 페이지 모스트 챔피언과 같은 숫자).
     */
    @Query("""
            SELECT mp.championId as championId,
                   mp.championName as championName,
                   COUNT(mp) as playCount,
                   SUM(CASE WHEN mp.win = true THEN 1 ELSE 0 END) as wins,
                   SUM(CASE WHEN mp.win = false THEN 1 ELSE 0 END) as losses
            FROM MatchParticipantEntity mp
            JOIN MatchEntity m ON m.matchId = mp.matchId
            JOIN MatchParticipantChallengesEntity c ON c.puuid = mp.puuid AND c.matchId = mp.matchId
            WHERE mp.puuid = :puuid AND m.queueId = :queueId
            GROUP BY mp.championId, mp.championName
            ORDER BY COUNT(mp) DESC, mp.championId ASC
            """)
    List<ChampionPlayCountProjection> findChampionPlayCounts(
            @Param("puuid") String puuid, @Param("queueId") int queueId, Pageable pageable);

    @Query("""
            SELECT mp.win as win,
                   mp.championId as championId,
                   mp.championName as championName
            FROM MatchParticipantEntity mp
            JOIN MatchEntity m ON m.matchId = mp.matchId
            WHERE mp.puuid = :puuid AND m.queueId = :queueId
            ORDER BY m.gameEndTimestamp DESC, m.matchId DESC
            """)
    List<RecentGameProjection> findRecentGames(
            @Param("puuid") String puuid, @Param("queueId") int queueId, Pageable pageable);
}
