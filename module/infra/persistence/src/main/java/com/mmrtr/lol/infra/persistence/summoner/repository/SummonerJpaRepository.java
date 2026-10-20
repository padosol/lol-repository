package com.mmrtr.lol.infra.persistence.summoner.repository;

import com.mmrtr.lol.infra.persistence.summoner.entity.SummonerEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SummonerJpaRepository extends JpaRepository<SummonerEntity, String> {

    List<SummonerEntity> findAllByPuuidIn(Collection<String> puuids);

    List<SummonerEntity> findByPlatformIdAndSearchName(String platformId, String searchName, Pageable pageable);

    /** StartingWith 는 값의 LIKE 특수문자(%, _)를 escape 한다. */
    List<SummonerEntity> findByPlatformIdAndSearchNameStartingWith(String platformId, String prefix,
                                                                   Pageable pageable);

    /**
     * 랭크 기록이 있는 소환사만 (lol-server 자동완성의 league_summoner INNER JOIN 과 같다).
     * pattern 은 {@code !} 로 escape 한 LIKE 패턴 — HQL 문자열 리터럴에서 {@code \} 는 escape 시퀀스로 읽힌다.
     */
    @Query("""
            SELECT s FROM SummonerEntity s
            WHERE s.platformId = :platformId
              AND s.searchName LIKE :pattern ESCAPE '!'
              AND EXISTS (SELECT 1 FROM LeagueSummonerEntity ls WHERE ls.puuid = s.puuid)
            ORDER BY s.searchName, s.puuid
            """)
    List<SummonerEntity> findRankedBySearchNameLike(@Param("platformId") String platformId,
                                                    @Param("pattern") String pattern,
                                                    Pageable pageable);
}
