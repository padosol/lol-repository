package com.mmrtr.lol.domain.league.repository;

import com.mmrtr.lol.domain.league.domain.SummonerRanking;

import java.util.List;
import java.util.Map;

public interface SummonerRankingRepositoryPort {

    List<SummonerRanking> saveAll(List<SummonerRanking> rankings);

    Map<String, Integer> findPreviousRanksByQueue(String queue);
}
