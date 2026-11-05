package com.mmrtr.lol.infra.riot.interceptor;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.infra.riot.exception.RiotBudgetExceededException;
import com.mmrtr.lol.infra.riot.ratelimit.RiotRateLimiter;
import com.mmrtr.lol.infra.riot.ratelimit.RiotRequestAttributes;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.redisson.client.RedisConnectionException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiotRateLimitInterceptorTest {

    private static final String METHOD = "match-v5.match";

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private FakeLimiter limiter;
    private List<Long> sleeps;
    private AtomicLong now;
    private SimpleMeterRegistry meterRegistry;
    private RestClient restClient;
    /** 인터셉터가 쓰는 [0, 1) 무작위 값. 기본 0 이면 흔들림이 없어 잔 시간이 대기 그대로다. */
    private double random;

    @BeforeEach
    void setUp() {
        wm.resetAll();
        limiter = new FakeLimiter();
        sleeps = new ArrayList<>();
        now = new AtomicLong();
        meterRegistry = new SimpleMeterRegistry();
        random = 0.0;
        build(advancingSleeper());
    }

    /** 잔 만큼 시계를 앞으로 옮기는 sleeper. */
    private RiotRateLimitInterceptor.Sleeper advancingSleeper() {
        return ms -> {
            sleeps.add(ms);
            now.addAndGet(ms);
        };
    }

    private void build(RiotRateLimitInterceptor.Sleeper sleeper) {
        RiotRateLimitInterceptor interceptor = new RiotRateLimitInterceptor(
                limiter, new RiotAPIProperties(), meterRegistry, sleeper, now::get, () -> random);
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(wm.baseUrl())
                .requestInterceptor(interceptor)
                .build();
    }

    private void stubOk() {
        wm.stubFor(get(urlEqualTo("/x")).willReturn(aResponse().withStatus(200).withBody("ok")));
    }

    private String call() {
        return restClient.get().uri("/x")
                .attribute(RiotRequestAttributes.METHOD, METHOD)
                .retrieve().body(String.class);
    }

    private String call(RiotCallPriority priority) {
        return restClient.get().uri("/x")
                .attribute(RiotRequestAttributes.METHOD, METHOD)
                .attribute(RiotRequestAttributes.PRIORITY, priority)
                .retrieve().body(String.class);
    }

    @Test
    @DisplayName("대기 시간이 주어지면 그만큼 sleep 후 보낸다")
    void grantedWait_sleepsThenSends() {
        stubOk();
        limiter.results.add(300L);

        call();

        assertThat(sleeps).containsExactly(300L);
        wm.verify(1, getRequestedFor(urlEqualTo("/x")));
    }

    @Test
    @DisplayName("허가(0)면 자지 않고 보내고, 같은 eventId 로 complete 한다")
    void zeroWait_sendsAndCompletesWithSameEventId() {
        stubOk();
        limiter.results.add(0L);

        call();

        assertThat(sleeps).isEmpty();
        assertThat(limiter.acquiredEventIds).hasSize(1);
        assertThat(limiter.completedEventIds).isEqualTo(limiter.acquiredEventIds);
    }

    @Test
    @DisplayName("양수 대기면 자고 같은 eventId 로 다시 묻는다")
    void positiveWait_sleepsAndAsksAgainWithSameEventId() {
        stubOk();
        limiter.results.addAll(List.of(300L, 0L));

        call();

        assertThat(sleeps).containsExactly(300L);
        assertThat(limiter.acquiredEventIds).hasSize(2);
        assertThat(limiter.acquiredEventIds.get(0)).isEqualTo(limiter.acquiredEventIds.get(1));
        assertThat(limiter.completedEventIds).hasSize(1);
    }

    @Test
    @DisplayName("USER 는 상한 안이면 계속 기다린다")
    void user_waitsWhileWithinCap() {
        stubOk();
        limiter.results.addAll(List.of(4_000L, 5_000L, 0L));

        assertThat(call(RiotCallPriority.USER)).isEqualTo("ok");

        assertThat(sleeps).containsExactly(4_000L, 5_000L);
    }

    @Test
    @DisplayName("USER 는 이번 대기로 상한을 넘으면 자지 않고 거절한다")
    void user_rejectsWithoutSleepingWhenCapWouldPass() {
        stubOk();
        limiter.results.addAll(List.of(4_000L, 7_000L));

        assertThatThrownBy(() -> call(RiotCallPriority.USER))
                .isInstanceOf(RiotBudgetExceededException.class);

        assertThat(sleeps).containsExactly(4_000L);
        wm.verify(0, getRequestedFor(urlEqualTo("/x")));
        assertThat(meterRegistry.counter("riot.ratelimit.rejected",
                "routing", "localhost", "priority", "USER").count()).isEqualTo(1.0);
        assertThat(limiter.completedEventIds).isEmpty();
    }

    @Test
    @DisplayName("BACKGROUND 는 남은 상한만큼만 자고 거절한다")
    void background_sleepsOnlyRemainingThenRejects() {
        stubOk();
        limiter.alwaysWait = 20_000L;

        assertThatThrownBy(() -> call(RiotCallPriority.BACKGROUND))
                .isInstanceOf(RiotBudgetExceededException.class);

        assertThat(sleeps).containsExactly(20_000L, 10_000L);
        wm.verify(0, getRequestedFor(urlEqualTo("/x")));
    }

    @Test
    @DisplayName("DEFERRED 의 상한은 60초다")
    void deferred_capIsSixtySeconds() {
        stubOk();
        limiter.alwaysWait = 50_000L;

        assertThatThrownBy(() -> call(RiotCallPriority.DEFERRED))
                .isInstanceOf(RiotBudgetExceededException.class);

        assertThat(sleeps).containsExactly(50_000L, 10_000L);
    }

    @Test
    @DisplayName("작은 대기는 최소 재시도 간격(25ms)으로 올려 잔다")
    void smallWait_isRaisedToRetryMinWait() {
        stubOk();
        limiter.results.addAll(List.of(1L, 0L));

        call();

        assertThat(sleeps).containsExactly(25L);
    }

    @Test
    @DisplayName("대기에 그 대기의 25% 까지 무작위로 더해 잔다")
    void jitter_addsUpToQuarterOfWait() {
        stubOk();
        random = 0.999_999;
        limiter.results.addAll(List.of(400L, 0L));

        call();

        assertThat(sleeps).hasSize(1);
        assertThat(sleeps.get(0)).isGreaterThan(400L).isLessThanOrEqualTo(500L);
    }

    @Test
    @DisplayName("무작위로 더한 대기도 남은 상한을 넘겨 자지 않는다")
    void jitter_isClampedToRemainingCap() {
        stubOk();
        random = 0.999;
        limiter.alwaysWait = 28_000L;

        assertThatThrownBy(() -> call(RiotCallPriority.BACKGROUND))
                .isInstanceOf(RiotBudgetExceededException.class);

        assertThat(sleeps).containsExactly(30_000L);
    }

    @Test
    @DisplayName("USER 는 흔들림 전 대기로 거절을 정하고, 흔들림을 더해도 상한까지만 잔다")
    void user_decidesOnRawWaitAndSleepsWithinCap() {
        stubOk();
        random = 0.999;
        limiter.results.addAll(List.of(4_000L, 5_000L, 0L));

        // 첫 잠은 약 5,000ms 라 누적 + 5,000 이 상한(10초) 안이다. 흔들림까지 더하면 넘지만 거절하지 않는다.
        assertThat(call(RiotCallPriority.USER)).isEqualTo("ok");

        assertThat(sleeps).hasSize(2);
        assertThat(sleeps.get(0)).isGreaterThan(4_000L);
        assertThat(sleeps.get(1)).isEqualTo(10_000L - sleeps.get(0));
    }

    @Test
    @DisplayName("limiter 에 묻는 횟수를 모두 센다")
    void acquireAttempts_countsEveryCall() {
        stubOk();
        limiter.results.addAll(List.of(300L, 0L));

        call();

        assertThat(meterRegistry.counter("riot.ratelimit.acquire.attempts",
                "routing", "localhost", "priority", "USER").count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("보낸 요청이 연결 끊김으로 끝나도 complete 한다")
    void executeFails_stillCompletes() {
        wm.stubFor(get(urlEqualTo("/x")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        limiter.results.add(0L);

        assertThatThrownBy(this::call)
                .isInstanceOf(ResourceAccessException.class)
                .hasCauseInstanceOf(IOException.class);

        assertThat(limiter.completedEventIds).hasSize(1).isEqualTo(limiter.acquiredEventIds);
    }

    @Test
    @DisplayName("complete 가 실패해도 응답은 반환한다")
    void completeFails_responseStillReturned() {
        stubOk();
        limiter.completeFailure = new RuntimeException("redis");

        assertThat(call()).isEqualTo("ok");
        assertThat(limiter.completedEventIds).hasSize(1);
    }

    @Test
    @DisplayName("429 응답도 complete 하고 정지는 그대로 한다")
    void status429_stillCompletes() {
        wm.stubFor(get(urlEqualTo("/x")).willReturn(aResponse().withStatus(429)
                .withHeader("Retry-After", "3")
                .withHeader("X-Rate-Limit-Type", "application")));
        limiter.results.add(0L);

        assertThatThrownBy(this::call)
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.TooManyRequests.class);

        assertThat(limiter.completedEventIds).hasSize(1).isEqualTo(limiter.acquiredEventIds);
        assertThat(limiter.pauses).containsExactly("localhost|null|PT3S");
    }

    @Test
    @DisplayName("응답 한도 헤더를 학습한다")
    void responseHeaders_learned() {
        wm.stubFor(get(urlEqualTo("/x")).willReturn(aResponse().withStatus(200)
                .withHeader("X-App-Rate-Limit", "500:10")
                .withHeader("X-Method-Rate-Limit", "2000:10")));
        limiter.results.add(0L);

        call();

        assertThat(limiter.learned).containsExactly("localhost|" + METHOD + "|500:10|2000:10");
    }

    @Test
    @DisplayName("application 429 는 routing 전체를 Retry-After 만큼 정지")
    void applicationLimit429_pausesRouting() {
        wm.stubFor(get(urlEqualTo("/x")).willReturn(aResponse().withStatus(429)
                .withHeader("Retry-After", "3")
                .withHeader("X-Rate-Limit-Type", "application")));
        limiter.results.add(0L);

        assertThatThrownBy(this::call).isInstanceOf(RuntimeException.class);

        assertThat(limiter.pauses).containsExactly("localhost|null|PT3S");
        assertThat(rateLimited("application")).isEqualTo(1.0);
        assertThat(meterRegistry.find("riot.ratelimit.near_limit").counter()).isNull();
        assertThat(meterRegistry.find("riot.ratelimit.throttled").counter()).isNull();
    }

    @Test
    @DisplayName("service 429 는 그 method 만 기본 1초 정지")
    void serviceLimit429_pausesMethodOnly() {
        wm.stubFor(get(urlEqualTo("/x")).willReturn(aResponse().withStatus(429)
                .withHeader("X-Rate-Limit-Type", "service")));
        limiter.results.add(0L);

        assertThatThrownBy(this::call).isInstanceOf(RuntimeException.class);

        assertThat(limiter.pauses).containsExactly("localhost|" + METHOD + "|PT1S");
        assertThat(rateLimited("service")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("X-Rate-Limit-Type 이 없는 429 는 type=none 으로 센다")
    void untyped429_countedAsNone() {
        wm.stubFor(get(urlEqualTo("/x")).willReturn(aResponse().withStatus(429)));
        limiter.results.add(0L);

        assertThatThrownBy(this::call).isInstanceOf(RuntimeException.class);

        assertThat(rateLimited("none")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("카운트가 한도의 95% 이상이면 1초 정지")
    void countHeaderNearLimit_pausesOneSecond() {
        wm.stubFor(get(urlEqualTo("/x")).willReturn(aResponse().withStatus(200)
                .withHeader("X-App-Rate-Limit", "500:10")
                .withHeader("X-App-Rate-Limit-Count", "476:10")));
        limiter.results.add(0L);

        call();

        assertThat(limiter.pauses).containsExactly("localhost|null|PT1S");
        assertThat(nearLimit("app")).isEqualTo(1.0);
        assertThat(meterRegistry.find("riot.api.rate_limited").counter()).isNull();
    }

    @Test
    @DisplayName("method 카운트가 한도의 95% 이상이면 그 method 만 1초 정지하고 scope=method 로 센다")
    void methodCountNearLimit_pausesMethod() {
        wm.stubFor(get(urlEqualTo("/x")).willReturn(aResponse().withStatus(200)
                .withHeader("X-Method-Rate-Limit", "2000:10")
                .withHeader("X-Method-Rate-Limit-Count", "1900:10")));
        limiter.results.add(0L);

        call();

        assertThat(limiter.pauses).containsExactly("localhost|" + METHOD + "|PT1S");
        assertThat(nearLimit("method")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("대기 중 interrupt 면 보내지 않고 IOException")
    void interruptedWhileWaiting_doesNotSend() {
        stubOk();
        limiter.results.add(300L);
        build((long ms) -> {
            throw new InterruptedException();
        });

        try {
            assertThatThrownBy(this::call).hasCauseInstanceOf(IOException.class);
            assertThat(Thread.interrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        wm.verify(0, getRequestedFor(urlEqualTo("/x")));
    }

    @Test
    @DisplayName("acquire 에서 Redis 오류면 그대로 전파하고 보내지 않는다")
    void redisFailure_failsWithoutSending() {
        stubOk();
        limiter.acquireFailure = new RedisConnectionException("down");

        assertThatThrownBy(this::call).isInstanceOf(RedisConnectionException.class);
        wm.verify(0, getRequestedFor(urlEqualTo("/x")));
    }

    @Test
    @DisplayName("응답 뒤 learnLimits 가 실패해도 응답은 반환한다")
    void learnFailure_afterResponse_returnsResponse() {
        stubOk();
        limiter.results.add(0L);
        limiter.learnFailure = new RuntimeException("redis down");

        assertThat(call()).isEqualTo("ok");
    }

    @Test
    @DisplayName("learnLimits 가 실패해도 429 정지는 수행하고 429 는 호출자에게 전달")
    void learnFailure_on429_stillPauses() {
        wm.stubFor(get(urlEqualTo("/x")).willReturn(aResponse().withStatus(429)
                .withHeader("Retry-After", "3")
                .withHeader("X-Rate-Limit-Type", "application")));
        limiter.results.add(0L);
        limiter.learnFailure = new RuntimeException("redis down");

        assertThatThrownBy(this::call)
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.TooManyRequests.class);

        assertThat(limiter.pauses).containsExactly("localhost|null|PT3S");
    }

    private double rateLimited(String type) {
        return meterRegistry.counter("riot.api.rate_limited", "routing", "localhost", "type", type).count();
    }

    private double nearLimit(String scope) {
        return meterRegistry.counter("riot.ratelimit.near_limit", "routing", "localhost", "scope", scope).count();
    }

    static class FakeLimiter implements RiotRateLimiter {
        final Deque<Long> results = new ArrayDeque<>();
        final List<String> learned = new ArrayList<>();
        final List<String> pauses = new ArrayList<>();
        final List<String> acquiredEventIds = new ArrayList<>();
        final List<String> completedEventIds = new ArrayList<>();
        final java.util.concurrent.atomic.AtomicInteger acquireCount =
                new java.util.concurrent.atomic.AtomicInteger();
        /** 0 이 아니면 acquire 가 늘 이 대기를 돌려준다. */
        long alwaysWait;
        RuntimeException acquireFailure;
        RuntimeException learnFailure;
        RuntimeException completeFailure;

        @Override
        public long acquire(String eventId, String routing, String method, RiotCallPriority priority) {
            acquireCount.incrementAndGet();
            acquiredEventIds.add(eventId);
            if (acquireFailure != null) {
                throw acquireFailure;
            }
            if (alwaysWait != 0L) {
                return alwaysWait;
            }
            return results.isEmpty() ? 0L : results.poll();
        }

        @Override
        public void complete(String eventId, String routing, String method, RiotCallPriority priority) {
            completedEventIds.add(eventId);
            if (completeFailure != null) {
                throw completeFailure;
            }
        }

        @Override
        public void learnLimits(String routing, String method, String app, String methodHeader) {
            if (learnFailure != null) {
                throw learnFailure;
            }
            learned.add(routing + "|" + method + "|" + app + "|" + methodHeader);
        }

        @Override
        public void pause(String routing, String methodOrNull, Duration duration) {
            pauses.add(routing + "|" + methodOrNull + "|" + duration);
        }
    }
}
