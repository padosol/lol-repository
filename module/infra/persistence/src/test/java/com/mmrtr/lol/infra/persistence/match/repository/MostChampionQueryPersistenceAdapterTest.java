package com.mmrtr.lol.infra.persistence.match.repository;

import com.mmrtr.lol.domain.match.readmodel.MostChampionPlayCount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class MostChampionQueryPersistenceAdapterTest {

    private final MatchSummonerJpaRepository repository = mock(MatchSummonerJpaRepository.class);
    private final MostChampionQueryPersistenceAdapter adapter = new MostChampionQueryPersistenceAdapter(repository);

    @Test
    @DisplayName("projection 을 순서대로 record 로 바꾼다")
    void projection을_순서대로_record로_바꾼다() {
        List<String> puuids = List.of("p1", "p2");
        given(repository.findMostChampionsByPuuids(puuids)).willReturn(List.of(
                projection("p1", "Ahri", 5), projection("p1", "Zed", 3), projection("p2", "Lux", 7)));

        assertThat(adapter.findMostChampionsByPuuids(puuids)).containsExactly(
                new MostChampionPlayCount("p1", "Ahri", 5),
                new MostChampionPlayCount("p1", "Zed", 3),
                new MostChampionPlayCount("p2", "Lux", 7));
    }

    private static MostChampionProjection projection(String puuid, String championName, long playCount) {
        return new MostChampionProjection() {
            @Override
            public String getPuuid() {
                return puuid;
            }

            @Override
            public String getChampionName() {
                return championName;
            }

            @Override
            public Long getPlayCount() {
                return playCount;
            }
        };
    }
}
