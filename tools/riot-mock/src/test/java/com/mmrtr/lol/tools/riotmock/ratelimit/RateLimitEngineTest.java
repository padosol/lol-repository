package com.mmrtr.lol.tools.riotmock.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateLimitEngineTest {

    private static final String MATCH = "match-v5.match";
    private static final String OTHER = "other-v1.endpoint";

    private final AtomicLong now = new AtomicLong(0);

    private RateLimitEngine engine(String appLimit, Map<String, String> methodLimits, boolean countRejected) {
        return new RateLimitEngine(appLimit, methodLimits, "1000:10", countRejected, now::get);
    }

    @Test
    @DisplayName("한도 이내면 허용하고 카운트 헤더를 준다")
    void acquire_한도이내면_허용하고_카운트헤더를_준다() {
        // given
        RateLimitEngine engine = engine("3:10", Map.of(), true);

        // when
        Verdict last = null;
        for (int i = 0; i < 3; i++) {
            last = engine.acquire("asia", OTHER);
            assertThat(last.allowed()).isTrue();
        }

        // then
        assertThat(last.appLimit()).isEqualTo("3:10");
        assertThat(last.appCount()).isEqualTo("3:10");
        assertThat(last.methodLimit()).isEqualTo("1000:10");
    }

    @Test
    @DisplayName("application 한도를 넘으면 거절하고 Retry-After 를 준다")
    void acquire_application한도를_넘으면_거절과_RetryAfter() {
        // given
        RateLimitEngine engine = engine("3:10", Map.of(), true);
        for (int i = 0; i < 3; i++) {
            engine.acquire("asia", OTHER);
        }
        now.set(2500);

        // when
        Verdict verdict = engine.acquire("asia", OTHER);

        // then
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.limitType()).isEqualTo("application");
        assertThat(verdict.retryAfterSeconds()).isEqualTo(8);
    }

    @Test
    @DisplayName("창이 끝나면 새 창을 연다")
    void acquire_창이_끝나면_새창을_연다() {
        // given
        RateLimitEngine engine = engine("3:10", Map.of(), true);
        for (int i = 0; i < 4; i++) {
            engine.acquire("asia", OTHER);
        }
        now.set(10000);

        // when
        Verdict verdict = engine.acquire("asia", OTHER);

        // then
        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.appCount()).isEqualTo("1:10");
    }

    @Test
    @DisplayName("창 끝 직전에도 Retry-After 는 최소 1")
    void acquire_창끝_직전에도_RetryAfter는_최소1() {
        // given
        RateLimitEngine engine = engine("3:10", Map.of(), true);
        for (int i = 0; i < 3; i++) {
            engine.acquire("asia", OTHER);
        }
        now.set(9999);

        // when
        Verdict verdict = engine.acquire("asia", OTHER);

        // then
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    @DisplayName("거절된 요청도 countRejected 면 수에 넣고 아니면 넣지 않는다")
    void acquire_거절도_수에_넣는다() {
        // given
        RateLimitEngine counting = engine("3:10", Map.of(), true);
        RateLimitEngine notCounting = engine("3:10", Map.of(), false);

        // when
        Verdict countingFifth = null;
        Verdict notCountingFifth = null;
        for (int i = 0; i < 5; i++) {
            countingFifth = counting.acquire("asia", OTHER);
            notCountingFifth = notCounting.acquire("asia", OTHER);
        }

        // then
        assertThat(countingFifth.appCount()).isEqualTo("5:10");
        assertThat(notCountingFifth.appCount()).isEqualTo("3:10");
    }

    @Test
    @DisplayName("method 한도를 넘으면 type 은 method")
    void acquire_method한도를_넘으면_type은_method() {
        // given
        RateLimitEngine engine = engine("100:10", Map.of(MATCH, "2:10"), true);
        engine.acquire("asia", MATCH);
        engine.acquire("asia", MATCH);

        // when
        Verdict verdict = engine.acquire("asia", MATCH);

        // then
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.limitType()).isEqualTo("method");
    }

    @Test
    @DisplayName("routing 별로 독립해서 센다")
    void acquire_routing별로_독립() {
        // given
        RateLimitEngine engine = engine("1:10", Map.of(), true);

        // when
        Verdict asia = engine.acquire("asia", OTHER);
        Verdict kr = engine.acquire("kr", OTHER);
        Verdict asiaAgain = engine.acquire("asia", OTHER);

        // then
        assertThat(asia.allowed()).isTrue();
        assertThat(kr.allowed()).isTrue();
        assertThat(asiaAgain.allowed()).isFalse();
    }

    @Test
    @DisplayName("여러 창을 모두 본다")
    void acquire_여러창을_모두_본다() {
        // given: 거절을 세지 않아야 10초 창이 6 에 머문다
        RateLimitEngine engine = engine("5:1,6:10", Map.of(), false);
        for (int i = 0; i < 5; i++) {
            assertThat(engine.acquire("asia", OTHER).allowed()).isTrue();
        }
        assertThat(engine.acquire("asia", OTHER).allowed()).isFalse();
        now.set(1000);

        // when
        Verdict allowed = engine.acquire("asia", OTHER);
        Verdict rejected = engine.acquire("asia", OTHER);

        // then
        assertThat(allowed.allowed()).isTrue();
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(9);
    }

    @Test
    @DisplayName("setAppLimit 은 지금 창의 수를 유지한다")
    void setAppLimit_지금창의_수를_유지한다() {
        // given
        RateLimitEngine engine = engine("10:10", Map.of(), true);
        for (int i = 0; i < 5; i++) {
            engine.acquire("asia", OTHER);
        }

        // when
        engine.setAppLimit("4:10");
        Verdict verdict = engine.acquire("asia", OTHER);

        // then
        assertThat(verdict.allowed()).isFalse();
    }

    @Test
    @DisplayName("버킷별 사용량과 한도를 준다")
    void usage_버킷별_사용량과_한도() {
        // given
        RateLimitEngine engine = engine("500:10", Map.of(), true);
        engine.acquire("asia", OTHER);

        // when
        var usage = engine.usage();

        // then
        assertThat(usage).contains(new BucketUsage("asia|app", 10, 1, 500));
    }
}
