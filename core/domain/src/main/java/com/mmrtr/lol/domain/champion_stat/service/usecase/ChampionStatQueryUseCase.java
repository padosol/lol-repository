package com.mmrtr.lol.domain.champion_stat.service.usecase;

import com.mmrtr.lol.domain.champion_stat.domain.*;

import java.util.List;

public interface ChampionStatQueryUseCase {

    List<ChampionStatSummary> getSummaries(int championId, String position, int season, String tierGroup, String platformId, int queueId, String patch);
    List<ChampionRuneStat> getRuneStats(int championId, String position, int season, String tierGroup, String platformId, int queueId, String patch);
    List<ChampionSpellStat> getSpellStats(int championId, String position, int season, String tierGroup, String platformId, int queueId, String patch);
    List<ChampionSkillStat> getSkillStats(int championId, String position, int season, String tierGroup, String platformId, int queueId, String patch);
    List<ChampionItemStat> getItemStats(int championId, String position, int season, String tierGroup, String platformId, int queueId, String patch);
    List<ChampionMatchupStat> getMatchupStats(int championId, String position, int season, String tierGroup, String platformId, int queueId, String patch);
}
