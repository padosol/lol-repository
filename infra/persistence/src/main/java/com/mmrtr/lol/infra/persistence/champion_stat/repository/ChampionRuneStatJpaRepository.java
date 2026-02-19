package com.mmrtr.lol.infra.persistence.champion_stat.repository;

import com.mmrtr.lol.infra.persistence.champion_stat.entity.ChampionRuneStatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChampionRuneStatJpaRepository extends JpaRepository<ChampionRuneStatEntity, Long> {

    void deleteByDimensionSeasonAndDimensionQueueId(int season, int queueId);

    List<ChampionRuneStatEntity> findByDimensionChampionIdAndDimensionTeamPositionAndDimensionSeasonAndDimensionTierGroupAndDimensionPlatformIdAndDimensionQueueIdAndDimensionGameVersion(
            int championId, String teamPosition, int season, String tierGroup, String platformId, int queueId, String gameVersion);
}
