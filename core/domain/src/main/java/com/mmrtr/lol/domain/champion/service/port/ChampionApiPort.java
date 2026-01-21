package com.mmrtr.lol.domain.champion.service.port;

import com.mmrtr.lol.domain.champion.domain.ChampionRotation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface ChampionApiPort {

    CompletableFuture<ChampionRotation> fetchChampionRotation(String platform, Executor executor);
}
