package com.mmrtr.lol.backfill.incremental;

import com.mmrtr.lol.backfill.incremental.TargetPatchPolicy.Hold;
import com.mmrtr.lol.backfill.incremental.TargetPatchPolicy.Proceed;
import com.mmrtr.lol.backfill.incremental.TargetPatchPolicy.TargetDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TargetPatchPolicyTest {

    private static PatchVersion v(String value) {
        return PatchVersion.parse(value);
    }

    @Test
    @DisplayName("패치 테이블이 비면 보류")
    void decide_emptyTable_holds() {
        // given / when
        TargetDecision decision = TargetPatchPolicy.decide(List.of(), Optional.of(v("16.21")));

        // then
        assertThat(decision).isInstanceOf(Hold.class);
    }

    @Test
    @DisplayName("범위에 테이블보다 새 패치가 있으면 보류")
    void decide_rangeHasNewerPatchThanTable_holds() {
        // given / when
        TargetDecision decision = TargetPatchPolicy.decide(List.of(v("16.21")), Optional.of(v("16.22")));

        // then
        assertThat(decision).isInstanceOf(Hold.class);
        assertThat(((Hold) decision).reason()).isNotBlank();
    }

    @Test
    @DisplayName("범위에 새 시즌 패치가 있으면 보류")
    void decide_rangeHasNewerSeason_holds() {
        // given / when
        TargetDecision decision = TargetPatchPolicy.decide(List.of(v("16.24")), Optional.of(v("17.1")));

        // then
        assertThat(decision).isInstanceOf(Hold.class);
    }

    @Test
    @DisplayName("범위가 테이블 안이면 테이블 최신 N개로 진행")
    void decide_rangeWithinTable_proceedsWithTablePatches() {
        // given
        List<PatchVersion> table = List.of(v("16.21"), v("16.20"));

        // when
        TargetDecision decision = TargetPatchPolicy.decide(table, Optional.of(v("16.21")));

        // then
        assertThat(decision).isEqualTo(new Proceed(table));
    }

    @Test
    @DisplayName("범위가 대상보다 모두 오래되면 빈 대상으로 진행")
    void decide_rangeOlderThanAllTargets_proceedsEmpty() {
        // given / when
        TargetDecision decision = TargetPatchPolicy.decide(List.of(v("16.21")), Optional.of(v("16.19")));

        // then
        assertThat(decision).isEqualTo(new Proceed(List.of()));
    }

    @Test
    @DisplayName("범위에 행이 없으면 빈 대상으로 진행")
    void decide_emptyRange_proceedsEmpty() {
        // given / when
        TargetDecision decision = TargetPatchPolicy.decide(List.of(v("16.21")), Optional.empty());

        // then
        assertThat(decision).isEqualTo(new Proceed(List.of()));
    }
}
