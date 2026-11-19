package com.mmrtr.lol.domain.match.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mmrtr.lol.domain.league.domain.LeagueSummoner;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MatchTierSnapshotTest {

    private static LeagueSummoner league(String puuid, int points) {
        return LeagueSummoner.builder().puuid(puuid).tier("GOLD").rank("II").absolutePoints(points).build();
    }

    @Test
    void 리그_정보가_있는_참가자만_평균한다() {
        MatchTierSnapshot s = MatchTierSnapshot.of(List.of("p1", "p2", "p3"),
                Map.of("p1", league("p1", 100), "p2", league("p2", 201)));

        assertThat(s.averageTier()).isEqualTo(150);
        assertThat(s.tierOf("p1")).contains(new MatchTierSnapshot.ParticipantTier("GOLD", "II", 100));
        assertThat(s.tierOf("p3")).isEmpty();
    }

    @Test
    void 평균은_소수점을_버린다() {
        MatchTierSnapshot s = MatchTierSnapshot.of(List.of("p1", "p2"),
                Map.of("p1", league("p1", 1), "p2", league("p2", 2)));

        assertThat(s.averageTier()).isEqualTo(1);
    }

    @Test
    void 절대점수_0도_평균에_들어간다() {
        MatchTierSnapshot s = MatchTierSnapshot.of(List.of("p1", "p2"),
                Map.of("p1", league("p1", 0), "p2", league("p2", 100)));

        assertThat(s.averageTier()).isEqualTo(50);
    }

    @Test
    void 리그_정보가_없으면_평균은_null() {
        assertThat(MatchTierSnapshot.of(List.of("p1"), Map.of()).averageTier()).isNull();
    }

    @Test
    void 빈_스냅샷은_평균도_티어도_없다() {
        MatchTierSnapshot s = MatchTierSnapshot.empty();

        assertThat(s.averageTier()).isNull();
        assertThat(s.tierOf("p1")).isEmpty();
    }
}
