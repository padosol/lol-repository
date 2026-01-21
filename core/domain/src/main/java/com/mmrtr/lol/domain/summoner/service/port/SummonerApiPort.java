package com.mmrtr.lol.domain.summoner.service.port;

import com.mmrtr.lol.domain.summoner.domain.Summoner;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface SummonerApiPort {

    CompletableFuture<Summoner> fetchSummonerByRiotId(
            String gameName, String tagLine, String platform, Executor executor);

    CompletableFuture<Summoner> fetchSummonerByPuuid(
            String puuid, String platform, Executor executor);
}
