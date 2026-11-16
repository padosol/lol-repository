package com.mmrtr.lol.tools.riotmock.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LabIdsTest {

    @Test
    @DisplayName("puuid 는 78자이고 번호로 역변환된다")
    void puuidOf_78자이고_역변환된다() {
        // given
        int n = 2501;

        // when
        String puuid = LabIds.puuidOf(1);
        String other = LabIds.puuidOf(n);

        // then
        assertThat(puuid).hasSize(78);
        assertThat(LabIds.indexOf(other).getAsInt()).isEqualTo(2501);
        assertThat(LabIds.indexOf("abc")).isEmpty();
        assertThat(LabIds.indexOf(LabIds.npcPuuidOf(5))).isEmpty();
    }

    @Test
    @DisplayName("매치 ID 세 형식이 모두 MatchRef 로 되돌아간다")
    void parseMatchId_세종류를_구분한다() {
        // given
        String personal = LabIds.personalMatchId(17, 29);
        String shared = LabIds.sharedMatchId(3, 12);
        String epoch = LabIds.epochMatchId(5, 3999, 2);

        // when
        MatchRef p = LabIds.parseMatchId(personal).orElseThrow();
        MatchRef s = LabIds.parseMatchId(shared).orElseThrow();
        MatchRef e = LabIds.parseMatchId(epoch).orElseThrow();

        // then
        assertThat(p).isEqualTo(LabIds.personalRef(17, 29));
        assertThat(p.kind()).isEqualTo(MatchRef.Kind.PERSONAL);
        assertThat(p.matchId()).isEqualTo(personal);
        assertThat(s).isEqualTo(LabIds.sharedRef(3, 12));
        assertThat(s.matchId()).isEqualTo(shared);
        assertThat(e).isEqualTo(LabIds.epochRef(5, 3999, 2));
        assertThat(e.owner()).isEqualTo(3999);
        assertThat(e.epoch()).isEqualTo(5);
        assertThat(e.index()).isEqualTo(2);
        assertThat(e.matchId()).isEqualTo(epoch);
        assertThat(LabIds.parseMatchId("KR_1")).isEmpty();
        assertThat(LabIds.parseMatchId("NA_7000000000")).isEmpty();
        assertThat(LabIds.parseMatchId("KR_abc")).isEmpty();
    }

    @Test
    @DisplayName("시작 시각은 T0 기준 규칙을 따른다")
    void refs_시작시각_규칙() {
        // given
        long min45 = 45L * 60_000;

        // then
        assertThat(LabIds.sharedRef(0, 0).startMillis()).isEqualTo(LabIds.T0);
        assertThat(LabIds.personalRef(1, 0).startMillis()).isEqualTo(LabIds.T0 - min45);
        assertThat(LabIds.sharedRef(0, 1).startMillis()).isEqualTo(LabIds.T0 - 2 * min45);
        assertThat(LabIds.epochRef(2, 3000, 1).startMillis()).isEqualTo(LabIds.T0 + 2 * 86_400_000L + min45);
    }
}
