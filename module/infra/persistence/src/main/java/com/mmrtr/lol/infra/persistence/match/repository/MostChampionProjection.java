package com.mmrtr.lol.infra.persistence.match.repository;

public interface MostChampionProjection {
    String getPuuid();
    String getChampionName();
    Long getPlayCount();
}
