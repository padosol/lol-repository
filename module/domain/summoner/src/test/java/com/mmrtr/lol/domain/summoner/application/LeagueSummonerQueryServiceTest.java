package com.mmrtr.lol.domain.summoner.application;

import com.mmrtr.lol.domain.summoner.application.port.LeagueSummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.domain.LeagueSummoner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LeagueSummonerQueryServiceTest {

    @Mock
    LeagueSummonerRepositoryPort leagueSummonerRepositoryPort;

    @InjectMocks
    LeagueSummonerQueryService service;

    @Test
    @DisplayName("포트_결과를_그대로_돌려준다")
    void 포트_결과를_그대로_돌려준다() {
        Set<String> puuids = Set.of("p1", "p2");
        List<LeagueSummoner> expected = List.of(LeagueSummoner.builder().puuid("p1").build());
        given(leagueSummonerRepositoryPort.findAllByPuuidsAndQueue(puuids, "RANKED_SOLO_5x5")).willReturn(expected);

        List<LeagueSummoner> result = service.findAllByPuuidsAndQueue(puuids, "RANKED_SOLO_5x5");

        assertThat(result).isSameAs(expected);
    }
}
