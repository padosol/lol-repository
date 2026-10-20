package com.mmrtr.lol.infra.persistence.spectator;

import com.mmrtr.lol.domain.spectator.application.port.SpectatorTargetPort;
import com.mmrtr.lol.domain.spectator.domain.SpectatorTarget;
import com.mmrtr.lol.infra.persistence.summoner.repository.SummonerJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpectatorTargetPersistenceAdapter implements SpectatorTargetPort {

    private final SummonerJpaRepository summonerJpaRepository;

    @Override
    public Optional<SpectatorTarget> findTarget(String puuid) {
        return summonerJpaRepository.findById(puuid)
                .map(summoner -> new SpectatorTarget(summoner.getPlatformId(), summoner.getRevisionDate()));
    }
}
