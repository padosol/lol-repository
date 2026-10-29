package com.mmrtr.lol.backfill.incremental;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatchVersionTest {

    @Test
    @DisplayName("숫자로 비교한다: 16.21 > 16.9, 17.1 > 16.24")
    void compareTo_numericValues_comparesNumerically() {
        // given / when / then
        assertThat(PatchVersion.parse("16.21")).isGreaterThan(PatchVersion.parse("16.9"));
        assertThat(PatchVersion.parse("17.1")).isGreaterThan(PatchVersion.parse("16.24"));
    }

    @Test
    @DisplayName("앞자리 0 은 정규화된다: 16.01 → 16.1")
    void parse_leadingZero_normalizes() {
        // given / when
        PatchVersion parsed = PatchVersion.parse("16.01");

        // then
        assertThat(parsed.value()).isEqualTo("16.1");
        assertThat(parsed.major()).isEqualTo(16);
        assertThat(parsed.minor()).isEqualTo(1);
    }

    @Test
    @DisplayName("형식이 아니면 예외")
    void parse_invalidFormat_throws() {
        // given / when / then
        for (String invalid : new String[]{"16", "16.21.1", null, "", "a.b", "16.", ".1"}) {
            assertThatThrownBy(() -> PatchVersion.parse(invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("join/split 왕복, 빈 문자열은 빈 목록")
    void joinSplit_roundTrip_restoresList() {
        // given
        List<PatchVersion> patches = List.of(PatchVersion.parse("16.21"), PatchVersion.parse("16.20"));

        // when
        String joined = PatchVersion.join(patches);

        // then
        assertThat(joined).isEqualTo("16.21,16.20");
        assertThat(PatchVersion.split(joined)).containsExactlyElementsOf(patches);
        assertThat(PatchVersion.split("")).isEmpty();
        assertThat(PatchVersion.join(List.of())).isEmpty();
    }
}
