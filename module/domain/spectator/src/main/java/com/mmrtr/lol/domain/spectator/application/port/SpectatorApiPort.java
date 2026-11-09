package com.mmrtr.lol.domain.spectator.application.port;

import com.mmrtr.lol.domain.spectator.domain.ActiveGame;
import com.mmrtr.lol.support.riot.RiotPermits;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface SpectatorApiPort {

    /** permits 에서 ACTIVE_GAME 허가 하나를 꺼내 쓴다. */
    CompletableFuture<Optional<ActiveGame>> fetchActiveGameByPuuid(
            String puuid, String platform, RiotPermits permits, Executor executor);
}
