package com.mmrtr.lol.infra.riot.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateLimitWindowTest {

    @Test
    @DisplayName("헤더의 모든 창을 읽는다")
    void parse_readsAllWindows() {
        assertThat(RateLimitWindow.parse("500:10,30000:600"))
                .containsExactly(new RateLimitWindow(500, 10), new RateLimitWindow(30000, 600));
    }

    @Test
    @DisplayName("잘못된 항목은 건너뛴다")
    void parse_skipsMalformedEntries() {
        assertThat(RateLimitWindow.parse("abc,500:10,5:0,500"))
                .containsExactly(new RateLimitWindow(500, 10));
        assertThat(RateLimitWindow.parse("")).isEmpty();
        assertThat(RateLimitWindow.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("같은 창에서 사용량이 95% 이상이면 한도 근접")
    void nearLimit_detectsNinetyFivePercent() {
        List<RateLimitWindow> limits = RateLimitWindow.parse("500:10");
        assertThat(RateLimitWindow.nearLimit(limits, RateLimitWindow.parse("475:10"), 0.95)).isTrue();
        assertThat(RateLimitWindow.nearLimit(limits, RateLimitWindow.parse("474:10"), 0.95)).isFalse();
        assertThat(RateLimitWindow.nearLimit(limits, RateLimitWindow.parse("475:600"), 0.95)).isFalse();
    }
}
