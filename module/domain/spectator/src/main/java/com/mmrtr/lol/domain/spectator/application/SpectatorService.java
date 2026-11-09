package com.mmrtr.lol.domain.spectator.application;

import com.mmrtr.lol.domain.spectator.domain.ActiveGame;
import com.mmrtr.lol.domain.spectator.application.port.SpectatorApiPort;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermitDeniedException;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
public class SpectatorService {

    private final SpectatorApiPort spectatorApiPort;
    private final RiotPermitPort riotPermitPort;
    private final Executor requestExecutor;

    public Optional<ActiveGame> getActiveGameByPuuid(String region, String puuid) {
        RiotPermits permits = riotPermitPort
                .tryAcquire(region, RiotCallPriority.USER, Map.of(RiotMethod.ACTIVE_GAME, 1))
                .orElseThrow(() -> new RiotPermitDeniedException(region, RiotCallPriority.USER));
        return spectatorApiPort.fetchActiveGameByPuuid(puuid, region, permits, requestExecutor).join();
    }
}
