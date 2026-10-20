package com.mmrtr.lol.infra.persistence.match.repository;

public interface RecentGameProjection {
    boolean getWin();
    int getChampionId();
    String getChampionName();
}
