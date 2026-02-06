package com.mmrtr.lol.domain.spectator.service;

import com.mmrtr.lol.domain.spectator.domain.ActiveGame;
import com.mmrtr.lol.domain.spectator.service.port.SpectatorApiPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
public class SpectatorService {

    private final SpectatorApiPort spectatorApiPort;
    private final Executor requestExecutor;

    public Optional<ActiveGame> getActiveGameByPuuid(String region, String puuid) {
        return spectatorApiPort.fetchActiveGameByPuuid(puuid, region, requestExecutor).join();
    }
}
