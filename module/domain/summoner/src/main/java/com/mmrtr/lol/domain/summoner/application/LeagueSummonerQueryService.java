package com.mmrtr.lol.domain.summoner.application;

import com.mmrtr.lol.domain.summoner.application.port.LeagueSummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.application.usecase.LeagueSummonerQueryUseCase;
import com.mmrtr.lol.domain.summoner.domain.LeagueSummoner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LeagueSummonerQueryService implements LeagueSummonerQueryUseCase {

    private final LeagueSummonerRepositoryPort leagueSummonerRepositoryPort;

    @Override
    public List<LeagueSummoner> findAllByPuuidsAndQueue(Set<String> puuids, String queue) {
        return leagueSummonerRepositoryPort.findAllByPuuidsAndQueue(puuids, queue);
    }
}
