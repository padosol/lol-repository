package com.mmrtr.lol.domain.championstats.domain;

import com.mmrtr.lol.common.type.Tier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** lol-server {@code TierFilterTest} 이전. */
class TierFilterTest {

    @DisplayName("단일 티어 입력 시 해당 티어만 포함한다")
    @Test
    void of_singleTier() {
        TierFilter filter = TierFilter.of("EMERALD");

        assertThat(filter.getTiers()).containsExactly(Tier.EMERALD);
        assertThat(filter.toDisplayString()).isEqualTo("EMERALD");
    }

    @DisplayName("MASTER+ 입력 시 MASTER 이상 티어를 모두 포함한다")
    @Test
    void of_masterPlus() {
        TierFilter filter = TierFilter.of("MASTER+");

        assertThat(filter.getTiers()).containsExactlyInAnyOrder(Tier.CHALLENGER, Tier.GRANDMASTER, Tier.MASTER);
        assertThat(filter.toDisplayString()).isEqualTo("MASTER+");
    }

    @DisplayName("CHALLENGER+ 입력 시 CHALLENGER만 포함한다")
    @Test
    void of_challengerPlus() {
        assertThat(TierFilter.of("CHALLENGER+").getTiers()).containsExactly(Tier.CHALLENGER);
    }

    @DisplayName("IRON+ 입력 시 모든 티어를 포함한다")
    @Test
    void of_ironPlus() {
        assertThat(TierFilter.of("IRON+").getTiers()).hasSize(10);
    }

    @DisplayName("null·빈 문자열·모르는 티어명은 IllegalArgumentException")
    @Test
    void of_invalid() {
        assertThatThrownBy(() -> TierFilter.of(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TierFilter.of("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TierFilter.of("INVALID")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TierFilter.of("INVALID+")).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("같은 티어 집합이면 같은 필터다")
    @Test
    void equals_sameTiers() {
        assertThat(TierFilter.of("CHALLENGER+")).isEqualTo(TierFilter.of("CHALLENGER"));
    }
}
