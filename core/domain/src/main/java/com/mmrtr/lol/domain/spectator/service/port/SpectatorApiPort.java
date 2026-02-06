package com.mmrtr.lol.domain.spectator.service.port;

import com.mmrtr.lol.domain.spectator.domain.ActiveGame;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface SpectatorApiPort {

    CompletableFuture<Optional<ActiveGame>> fetchActiveGameByPuuid(
            String puuid, String platform, Executor executor);
}
