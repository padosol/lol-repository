package com.mmrtr.lol.infra.persistence.champion_stat.repository;

import com.mmrtr.lol.infra.persistence.champion_stat.entity.ChampionSkillStatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChampionSkillStatJpaRepository extends JpaRepository<ChampionSkillStatEntity, Long> {

    void deleteByDimensionSeasonAndDimensionQueueId(int season, int queueId);

    List<ChampionSkillStatEntity> findByDimensionChampionIdAndDimensionTeamPositionAndDimensionSeasonAndDimensionTierGroupAndDimensionPlatformIdAndDimensionQueueIdAndDimensionGameVersion(
            int championId, String teamPosition, int season, String tierGroup, String platformId, int queueId, String gameVersion);
}
