package com.mmrtr.lol.infra.persistence.match.repository;

public interface ChampionPlayCountProjection {
    int getChampionId();
    String getChampionName();
    long getPlayCount();
    long getWins();
    long getLosses();
}
