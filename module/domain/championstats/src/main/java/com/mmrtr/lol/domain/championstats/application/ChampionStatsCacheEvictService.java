package com.mmrtr.lol.domain.championstats.application;

import com.mmrtr.lol.domain.championstats.application.port.ChampionStatsCachePort;
import com.mmrtr.lol.domain.championstats.application.usecase.ChampionStatsCacheEvictUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChampionStatsCacheEvictService implements ChampionStatsCacheEvictUseCase {

    private final ChampionStatsCachePort cachePort;

    @Override
    public long evictAll() {
        return cachePort.evictAll();
    }
}
