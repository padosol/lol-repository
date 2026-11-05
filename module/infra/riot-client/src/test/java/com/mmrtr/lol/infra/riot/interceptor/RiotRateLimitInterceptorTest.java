package com.mmrtr.lol.infra.riot.interceptor;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
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

    @BeforeEach
    void setUp() {
        wm.resetAll();
        limiter = new FakeLimiter();
        sleeps = new ArrayList<>();
        now = new AtomicLong();
        meterRegistry = new SimpleMeterRegistry();
        build((long ms) -> sleeps.add(ms));
    }

    private void build(RiotRateLimitInterceptor.Sleeper sleeper) {
        RiotRateLimitInterceptor interceptor = new RiotRateLimitInterceptor(
                limiter, new RiotAPIProperties(), meterRegistry, sleeper, now::get);
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
