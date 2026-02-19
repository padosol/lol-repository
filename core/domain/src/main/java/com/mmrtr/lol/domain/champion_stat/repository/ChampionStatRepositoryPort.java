package com.mmrtr.lol.domain.champion_stat.repository;

import com.mmrtr.lol.domain.champion_stat.domain.*;

import java.util.List;

public interface ChampionStatRepositoryPort {

    // 삭제
    void deleteAllBySeasonAndQueueId(int season, int queueId);

    // 벌크 저장
    void bulkSaveSummaries(List<ChampionStatSummary> summaries);
    void bulkSaveRuneStats(List<ChampionRuneStat> runeStats);
    void bulkSaveSpellStats(List<ChampionSpellStat> spellStats);
    void bulkSaveSkillStats(List<ChampionSkillStat> skillStats);
    void bulkSaveItemStats(List<ChampionItemStat> itemStats);
    void bulkSaveMatchupStats(List<ChampionMatchupStat> matchupStats);

    // 조회
    List<ChampionStatSummary> findSummaries(int championId, String teamPosition, int season, String tierGroup, String platformId, int queueId, String gameVersion);
    List<ChampionRuneStat> findRuneStats(int championId, String teamPosition, int season, String tierGroup, String platformId, int queueId, String gameVersion);
    List<ChampionSpellStat> findSpellStats(int championId, String teamPosition, int season, String tierGroup, String platformId, int queueId, String gameVersion);
    List<ChampionSkillStat> findSkillStats(int championId, String teamPosition, int season, String tierGroup, String platformId, int queueId, String gameVersion);
    List<ChampionItemStat> findItemStats(int championId, String teamPosition, int season, String tierGroup, String platformId, int queueId, String gameVersion);
    List<ChampionMatchupStat> findMatchupStats(int championId, String teamPosition, int season, String tierGroup, String platformId, int queueId, String gameVersion);
}
