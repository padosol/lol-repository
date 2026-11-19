package com.mmrtr.lol.domain.match.application;

import com.mmrtr.lol.domain.match.application.port.MostChampionQueryPort;
import com.mmrtr.lol.domain.match.readmodel.MostChampionPlayCount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class MostChampionQueryServiceTest {

    private final MostChampionQueryPort port = mock(MostChampionQueryPort.class);
    private final MostChampionQueryService service = new MostChampionQueryService(port);

    @Test
    @DisplayName("포트 결과를 순서대로 돌려준다")
    void 포트_결과를_순서대로_돌려준다() {
        List<MostChampionPlayCount> expected = List.of(
                new MostChampionPlayCount("p1", "Ahri", 5),
                new MostChampionPlayCount("p2", "Lux", 7));
        given(port.findMostChampionsByPuuids(List.of("p1", "p2"))).willReturn(expected);

        assertThat(service.findMostChampionsByPuuids(List.of("p1", "p2"))).containsExactlyElementsOf(expected);
    }
}
