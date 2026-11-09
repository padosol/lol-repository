package com.mmrtr.lol.domain.champion.application.port;

import com.mmrtr.lol.domain.champion.domain.ChampionRotation;
import com.mmrtr.lol.support.riot.RiotPermits;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface ChampionApiPort {

    /** permits 에서 CHAMPION_ROTATION 허가 하나를 꺼내 쓴다. */
    CompletableFuture<ChampionRotation> fetchChampionRotation(String platform, RiotPermits permits, Executor executor);
}
