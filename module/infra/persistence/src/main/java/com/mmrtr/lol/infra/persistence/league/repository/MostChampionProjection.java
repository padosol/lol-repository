package com.mmrtr.lol.infra.persistence.league.repository;

public interface MostChampionProjection {
    String getPuuid();
    String getChampionName();
    Long getPlayCount();
}
