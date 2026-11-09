package com.mmrtr.lol.domain.champion.application;

import com.mmrtr.lol.domain.champion.domain.ChampionRotation;
import com.mmrtr.lol.domain.champion.application.port.ChampionApiPort;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermitDeniedException;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
public class ChampionRotateService {

    private final ChampionApiPort championApiPort;
    private final RiotPermitPort riotPermitPort;
    private final Executor requestExecutor;

    public ChampionRotation getChampionRotate(String region) {
        RiotPermits permits = riotPermitPort
                .tryAcquire(region, RiotCallPriority.USER, Map.of(RiotMethod.CHAMPION_ROTATION, 1))
                .orElseThrow(() -> new RiotPermitDeniedException(region, RiotCallPriority.USER));
        return championApiPort.fetchChampionRotation(region, permits, requestExecutor).join();
    }
}
