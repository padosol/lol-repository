package com.mmrtr.lol.infra.persistence.league.repository;

public interface SummonerRankingProjection {
    String getPuuid();
    String getQueue();
    String getTier();
    String getRank();
    Integer getLeaguePoints();
    Integer getWins();
    Integer getLosses();
    Integer getAbsolutePoints();
    String getGameName();
    String getTagLine();
}
