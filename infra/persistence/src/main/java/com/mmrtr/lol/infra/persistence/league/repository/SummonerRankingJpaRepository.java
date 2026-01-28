package com.mmrtr.lol.infra.persistence.league.repository;

import com.mmrtr.lol.infra.persistence.league.entity.SummonerRankingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SummonerRankingJpaRepository extends JpaRepository<SummonerRankingEntity, Long> {

    @Query("""
            SELECT sr FROM SummonerRankingEntity sr
            WHERE sr.queue = :queue
              AND sr.snapshotAt = (
                  SELECT MAX(sr2.snapshotAt)
                  FROM SummonerRankingEntity sr2
                  WHERE sr2.queue = :queue
              )
            """)
    List<SummonerRankingEntity> findLatestByQueue(@Param("queue") String queue);
}
