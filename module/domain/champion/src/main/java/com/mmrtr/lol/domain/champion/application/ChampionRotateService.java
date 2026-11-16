package com.mmrtr.lol.domain.champion.application;

import com.mmrtr.lol.domain.champion.domain.ChampionRotation;
import com.mmrtr.lol.domain.champion.application.port.ChampionApiPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
public class ChampionRotateService {

    private final ChampionApiPort championApiPort;
    private final Executor requestExecutor;

    public ChampionRotation getChampionRotate(String region) {
        return championApiPort.fetchChampionRotation(region, requestExecutor).join();
    }
}
