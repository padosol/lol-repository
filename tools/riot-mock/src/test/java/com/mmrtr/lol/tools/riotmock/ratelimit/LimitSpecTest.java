package com.mmrtr.lol.tools.riotmock.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LimitSpecTest {

    @Test
    @DisplayName("여러 창을 순서대로 읽고 format 으로 되돌리면 원문과 같다")
    void parseAll_여러창을_순서대로_읽는다() {
        // given
        String header = "500:10,30000:600";

        // when
        List<LimitSpec> specs = LimitSpec.parseAll(header);

        // then
        assertThat(specs).containsExactly(new LimitSpec(500, 10), new LimitSpec(30000, 600));
        assertThat(LimitSpec.format(specs)).isEqualTo(header);
    }

    @Test
    @DisplayName("형식이 틀리면 IllegalArgumentException")
    void parseAll_형식이_틀리면_예외() {
        // given
        String header = "500";

        // when & then
        assertThatThrownBy(() -> LimitSpec.parseAll(header)).isInstanceOf(IllegalArgumentException.class);
    }
}
