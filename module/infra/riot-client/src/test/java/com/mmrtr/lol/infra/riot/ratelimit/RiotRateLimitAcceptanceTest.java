package com.mmrtr.lol.infra.riot.ratelimit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.infra.riot.interceptor.ConcurrencyInterceptor;
import com.mmrtr.lol.infra.riot.interceptor.RetryInterceptor;
import com.mmrtr.lol.infra.riot.interceptor.RiotRateLimitInterceptor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 실제 Redis limiter + 인터셉터 체인으로 고정 창(20:2) 대역을 지키는지 확인한다. */
@Testcontainers
class RiotRateLimitAcceptanceTest {

    private static final int LIMIT = 20;
    private static final long WINDOW_MS = 2000;

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static RedissonClient redisson;

    @BeforeAll
    static void connect() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        redisson = Redisson.create(config);
    }

    @AfterAll
    static void disconnect() {
        redisson.shutdown();
    }

    @BeforeEach
    void setUp() {
        redisson.getKeys().flushall();
        wm.resetAll();
        wm.stubFor(get(urlEqualTo("/ok")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("X-App-Rate-Limit", "20:2")));
    }

    @Test
    @DisplayName("USER 호출 30건을 동시에 보내도 어느 2초 구간에도 20건을 넘지 않는다")
    void concurrentUserCalls_neverExceedFixedWindow() throws Exception {
        RestClient client = newClient();

        List<HttpStatusCode> statuses = fire(client, 30);

        assertThat(statuses).hasSize(30).allMatch(s -> s.value() == 200);
        assertWithinWindow(30);
    }

    @Test
    @DisplayName("중간에 limiter 를 새로 만들어도(재시작) 같은 한도를 지킨다")
    void restartMidway_stillWithinLimit() throws Exception {
        List<HttpStatusCode> statuses = new ArrayList<>(fire(newClient(), 15));
        // 같은 Redis, 새 limiter·인터셉터
        statuses.addAll(fire(newClient(), 15));

        assertThat(statuses).hasSize(30).allMatch(s -> s.value() == 200);
        assertWithinWindow(30);
    }

    private RestClient newClient() {
        RiotAPIProperties props = new RiotAPIProperties();
        props.getRateLimit().setAppDefault("20:2");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RiotRateLimiter limiter = new RedisRiotRateLimiter(redisson, props);
        RiotRateLimitInterceptor rateLimit = new RiotRateLimitInterceptor(limiter, props, registry);
        RetryInterceptor retry = new RetryInterceptor(registry,
                List.of(rateLimit, new ConcurrencyInterceptor(20)));

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(wm.baseUrl())
                .requestInterceptor(retry)
                .build();
    }

    private List<HttpStatusCode> fire(RestClient client, int count) throws Exception {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<HttpStatusCode>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(pool.submit(() -> client.get().uri("/ok").retrieve()
                        .toBodilessEntity().getStatusCode()));
            }
            List<HttpStatusCode> result = new ArrayList<>();
            for (Future<HttpStatusCode> f : futures) {
                result.add(f.get(60, TimeUnit.SECONDS));
            }
            return result;
        }
    }

    /** 요청 시각 기준 슬라이딩 2초 구간(고정 창보다 엄격)마다 20건 이하인지 본다. */
    private void assertWithinWindow(int expectedTotal) {
        List<Long> times = wm.getAllServeEvents().stream()
                .map(ServeEvent::getRequest)
                .map(r -> r.getLoggedDate().getTime())
                .sorted()
                .toList();
        assertThat(times).hasSize(expectedTotal);

        int max = 0;
        for (long from : times) {
            int count = 0;
            for (long t : times) {
                if (t >= from && t < from + WINDOW_MS) {
                    count++;
                }
            }
            max = Math.max(max, count);
        }
        System.out.println("ACCEPTANCE max-per-2s=" + max + " span="
                + (times.get(times.size() - 1) - times.get(0)) + "ms");
        assertThat(max).isLessThanOrEqualTo(LIMIT);
    }
}
