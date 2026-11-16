package com.mmrtr.lol.infra.riot.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("Count 헤더는 0 도 읽고, 창이 0 이하이거나 잘못된 항목은 건너뛴다")
    void parseCounts_allowsZeroCount() {
        assertThat(RateLimitWindow.parseCounts("0:10,40:600"))
                .containsExactly(new RateLimitWindow(0, 10), new RateLimitWindow(40, 600));
        assertThat(RateLimitWindow.parseCounts("abc,-1:10,3:0,3:10"))
                .containsExactly(new RateLimitWindow(3, 10));
        assertThat(RateLimitWindow.parseCounts(null)).isEmpty();
    }
}
