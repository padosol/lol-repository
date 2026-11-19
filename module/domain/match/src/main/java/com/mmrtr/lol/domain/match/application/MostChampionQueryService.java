package com.mmrtr.lol.domain.match.application;

import com.mmrtr.lol.domain.match.application.port.MostChampionQueryPort;
import com.mmrtr.lol.domain.match.application.usecase.MostChampionQueryUseCase;
import com.mmrtr.lol.domain.match.readmodel.MostChampionPlayCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MostChampionQueryService implements MostChampionQueryUseCase {

    private final MostChampionQueryPort mostChampionQueryPort;

    @Override
    public List<MostChampionPlayCount> findMostChampionsByPuuids(List<String> puuids) {
        return mostChampionQueryPort.findMostChampionsByPuuids(puuids);
    }
}
