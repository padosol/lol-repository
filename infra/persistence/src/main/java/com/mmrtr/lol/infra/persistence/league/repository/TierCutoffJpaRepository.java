package com.mmrtr.lol.infra.persistence.league.repository;

import com.mmrtr.lol.infra.persistence.league.entity.TierCutoffEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TierCutoffJpaRepository extends JpaRepository<TierCutoffEntity, Long> {

    List<TierCutoffEntity> findByQueue(String queue);

    @Modifying
    @Query("DELETE FROM TierCutoffEntity tc WHERE tc.queue = :queue")
    void deleteByQueue(@Param("queue") String queue);

    @Modifying
    @Query(value = """
            INSERT INTO tier_cutoff_backup (queue, tier, region, min_league_points, user_count)
            SELECT queue, tier, region, min_league_points, user_count
            FROM tier_cutoff WHERE queue = :queue
            """, nativeQuery = true)
    void backupCurrentCutoffs(@Param("queue") String queue);

    @Modifying
    @Query(value = """
            UPDATE tier_cutoff tc
            SET lp_change = tc.min_league_points - COALESCE(backup.min_league_points, tc.min_league_points)
            FROM tier_cutoff_backup backup
            WHERE tc.queue = backup.queue
              AND tc.tier = backup.tier
              AND tc.region = backup.region
              AND tc.queue = :queue
            """, nativeQuery = true)
    void updateLpChangesFromBackup(@Param("queue") String queue);

    @Modifying
    @Query(value = "DELETE FROM tier_cutoff_backup WHERE queue = :queue", nativeQuery = true)
    void clearBackup(@Param("queue") String queue);
}
