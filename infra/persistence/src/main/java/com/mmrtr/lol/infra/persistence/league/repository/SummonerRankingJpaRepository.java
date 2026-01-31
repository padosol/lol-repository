package com.mmrtr.lol.infra.persistence.league.repository;

import com.mmrtr.lol.infra.persistence.league.entity.SummonerRankingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SummonerRankingJpaRepository extends JpaRepository<SummonerRankingEntity, Long> {

    @Modifying
    @Query("DELETE FROM SummonerRankingEntity sr WHERE sr.queue = :queue")
    void deleteByQueue(@Param("queue") String queue);

    @Modifying
    @Query(value = """
            INSERT INTO summoner_ranking_backup (puuid, queue, current_rank)
            SELECT puuid, queue, current_rank FROM summoner_ranking WHERE queue = :queue
            """, nativeQuery = true)
    void backupCurrentRanks(@Param("queue") String queue);

    @Modifying
    @Query(value = """
            UPDATE summoner_ranking sr
            SET rank_change = COALESCE(backup.current_rank - sr.current_rank, 0)
            FROM summoner_ranking_backup backup
            WHERE sr.puuid = backup.puuid
              AND sr.queue = backup.queue
              AND sr.queue = :queue
            """, nativeQuery = true)
    void updateRankChangesFromBackup(@Param("queue") String queue);

    @Modifying
    @Query(value = "DELETE FROM summoner_ranking_backup WHERE queue = :queue", nativeQuery = true)
    void clearBackup(@Param("queue") String queue);
}
