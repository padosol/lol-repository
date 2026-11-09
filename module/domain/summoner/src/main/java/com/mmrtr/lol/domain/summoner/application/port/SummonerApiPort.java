package com.mmrtr.lol.domain.summoner.application.port;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermits;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Riot 소환사 조회. 호출하는 쪽이 아래 비용만큼 받은 허가를 넘긴다. */
public interface SummonerApiPort {

    /** Riot ID 조회: account(by-riot-id) 1 + summoner 1 + league 1. */
    Map<RiotMethod, Integer> BY_RIOT_ID_COST = Map.of(
            RiotMethod.ACCOUNT_BY_RIOT_ID, 1,
            RiotMethod.SUMMONER_BY_PUUID, 1,
            RiotMethod.LEAGUE_ENTRIES_BY_PUUID, 1);

    /** puuid 조회: account(by-puuid) 1 + summoner 1 + league 1. */
    Map<RiotMethod, Integer> BY_PUUID_COST = Map.of(
            RiotMethod.ACCOUNT_BY_PUUID, 1,
            RiotMethod.SUMMONER_BY_PUUID, 1,
            RiotMethod.LEAGUE_ENTRIES_BY_PUUID, 1);

    CompletableFuture<Summoner> fetchSummonerByRiotId(
            String gameName, String tagLine, String platform, RiotPermits permits, Executor executor);

    CompletableFuture<Summoner> fetchSummonerByPuuid(
            String puuid, String platform, RiotPermits permits, Executor executor);
}
