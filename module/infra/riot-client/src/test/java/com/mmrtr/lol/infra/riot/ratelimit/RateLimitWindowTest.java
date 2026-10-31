package com.mmrtr.lol.infra.riot.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateLimitWindowTest {

    private static final double SAFETY = 0.9;

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
    @DisplayName("10초 창은 1초 칸에 floor(min(limit*0.9/W, limit/(W+1))) 회")
    void allowance_tenSecondWindow_matchesSpec() {
        RateLimitWindow w = new RateLimitWindow(500, 10);
        assertThat(w.bucketSeconds(SAFETY)).isEqualTo(1);
        assertThat(w.allowance(SAFETY)).isEqualTo(45);
        assertThat(new RateLimitWindow(300, 10).allowance(SAFETY)).isEqualTo(27);
    }

    @Test
    @DisplayName("짧은 창은 더 엄격하다")
    void allowance_shortWindow_isStricter() {
        RateLimitWindow w = new RateLimitWindow(20, 1);
        assertThat(w.bucketSeconds(SAFETY)).isEqualTo(1);
        assertThat(w.allowance(SAFETY)).isEqualTo(10);
    }

    @Test
    @DisplayName("느린 창은 W초 칸을 쓴다")
    void allowance_slowWindow_usesWholeWindowBucket() {
        RateLimitWindow a = new RateLimitWindow(500, 600);
        assertThat(a.bucketSeconds(SAFETY)).isEqualTo(600);
        assertThat(a.allowance(SAFETY)).isEqualTo(225);
        RateLimitWindow b = new RateLimitWindow(100, 120);
        assertThat(b.bucketSeconds(SAFETY)).isEqualTo(120);
        assertThat(b.allowance(SAFETY)).isEqualTo(45);
    }

    @Test
    @DisplayName("초당 허용치는 창별 최솟값")
    void callsPerSecond_takesMinimum() {
        assertThat(RateLimitWindow.callsPerSecond(
                RateLimitWindow.parse("500:10,30000:600"), SAFETY)).isEqualTo(45);
        assertThat(RateLimitWindow.callsPerSecond(
                RateLimitWindow.parse("30:10,500:600"), SAFETY)).isEqualTo(1);
        assertThat(RateLimitWindow.callsPerSecond(List.of(), SAFETY)).isEqualTo(Integer.MAX_VALUE);
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
