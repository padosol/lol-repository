package com.mmrtr.lol.infra.riot.ratelimit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.infra.riot.config.RiotApiConfig;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import com.mmrtr.lol.infra.riot.service.RiotCallRecorder;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermit;
import com.mmrtr.lol.support.riot.RiotPermits;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 Redis 한도 관리자 + {@link RiotApiService} 로 한도 20:2 의 상한 18(floor(20 × 0.9))을 지키는지 확인한다.
 * 호출은 단건 허가를 받은 것만 보낸다.
 *
 * <p>한도는 허가 시각으로만 세므로, 설계가 보장하는 것은 "Redis 에 기록된 허가 시각 기준 어느 2초 구간에도 18건 이하" 다.
 * 그래서 그것을 본다. 가짜 서버가 받은 시각 기준으로는 허가 → 도착 지연이 흔들린 만큼 넘을 수 있다 (2026-11-11 확인:
 * 차가운 첫 묶음이면 30건이 한 2초 구간에 들고, 데워도 4번 중 1번은 21건). 설계 2026-11-11 9장 위험 1 로 받아들이고,
 * 실제 넘침은 부하 테스트의 Riot 429 로 본다.
 */
@Testcontainers
class RiotRateLimitAcceptanceTest {

    /** 한도 20:2 의 상한 = floor(20 × 0.9). */
    private static final int CAP = 18;
    private static final long WINDOW_MS = 2000;
    private static final int RESPONSE_DELAY_MS = 200;
    /** 거절되면 다시 묻기 전 테스트 스레드가 자는 시간. */
    private static final long RETRY_SLEEP_MS = 20;
    private static final String APP_2 = RedisRiotRateLimiter.ruleKey("asia|app", 2);

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static RedissonClient redisson;

    /** 받은 허가의 허가 시각 (Redis 에 기록된 토큰 점수, Redis TIME 기준 ms). */
    private final List<Long> permitTimes = new CopyOnWriteArrayList<>();

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
        wm.stubFor(get(urlPathMatching("/asia/lol/match/v5/matches/.*")).willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(RESPONSE_DELAY_MS)
                .withHeader("Content-Type", "application/json")
                .withHeader("X-App-Rate-Limit", "20:2")
                .withBody("{}")));
    }

    @Test
    @DisplayName("스레드 30개가 허가받은 호출만 보내면 허가 시각 기준 어느 2초 구간에도 상한(18)을 넘지 않는다")
    void concurrentPermittedCalls_neverExceedWindow() throws Exception {
        Client client = newClient();

        int ok = fire(client, 30);

        assertThat(ok).isEqualTo(30);
        assertWithinWindow(30);
    }

    @Test
    @DisplayName("중간에 한도 관리자를 새로 만들어도(재시작) 같은 한도를 지킨다")
    void restartMidway_stillWithinLimit() throws Exception {
        int ok = fire(newClient(), 15);
        // 같은 Redis, 새 한도 관리자·RiotApiService
        ok += fire(newClient(), 15);

        assertThat(ok).isEqualTo(30);
        assertWithinWindow(30);
    }

    @Test
    @DisplayName("상한(18) 안의 동시 호출은 거절 없이 바로 나간다")
    void burstWithinCap_servedWithoutDenial() throws Exception {
        int ok = fire(newClient(), 18);

        assertThat(ok).isEqualTo(18);
        List<Long> times = receivedTimes();
        assertThat(times.get(times.size() - 1) - times.get(0)).isLessThan(1000L);
    }

    private record Client(RedisRiotRateLimiter limiter, RiotApiService service) {
    }

    private Client newClient() {
        RiotAPIProperties props = new RiotAPIProperties();
        props.setApiKey("test-key");
        props.getRateLimit().setAppLimits("20:2");
        props.getMock().setBaseUrl(wm.baseUrl());
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local", "lab");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RedisRiotRateLimiter limiter = new RedisRiotRateLimiter(redisson, props, registry);
        RiotApiService service = new RiotApiService(new RiotApiConfig(props, environment).riotRestClient(),
                new RiotCallRecorder(limiter, props, registry));
        return new Client(limiter, service);
    }

    /** 스레드마다 단건 허가를 받을 때까지 묻고(거절이면 잠깐 잔다), 받은 허가로만 한 번 부른다. 성공 수를 돌려준다. */
    private int fire(Client client, int count) throws Exception {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String matchId = "KR_" + i;
                futures.add(pool.submit(() -> {
                    RiotPermit permit = awaitPermit(client.limiter());
                    return client.service().getMatchById(matchId, Platform.KR, permit, Runnable::run).join() != null;
                }));
            }
            int ok = 0;
            for (Future<Boolean> f : futures) {
                if (f.get(60, TimeUnit.SECONDS)) {
                    ok++;
                }
            }
            return ok;
        }
    }

    /** 허가를 받을 때까지 묻고, 받으면 그 토큰의 허가 시각을 app 규칙 ZSET 에서 읽어 둔다. */
    private RiotPermit awaitPermit(RedisRiotRateLimiter limiter) throws InterruptedException {
        while (true) {
            Optional<RiotPermits> permits = limiter.tryAcquire("KR", RiotCallPriority.USER,
                    Map.of(RiotMethod.MATCH, 1));
            if (permits.isPresent()) {
                RiotPermit permit = permits.get().take(RiotMethod.MATCH);
                Double score = redisson.getScoredSortedSet(APP_2, StringCodec.INSTANCE).getScore(permit.token());
                assertThat(score).as("허가 직후 토큰 기록").isNotNull();
                permitTimes.add(score.longValue());
                return permit;
            }
            Thread.sleep(RETRY_SLEEP_MS);
        }
    }

    private static List<Long> receivedTimes() {
        return wm.getAllServeEvents().stream()
                .map(ServeEvent::getRequest)
                .map(r -> r.getLoggedDate().getTime())
                .sorted()
                .toList();
    }

    /** 가짜 서버가 모두 받았고, 허가 시각 기준 슬라이딩 2초 구간(고정 창보다 엄격)마다 상한 이하인지 본다. */
    private void assertWithinWindow(int expectedTotal) {
        assertThat(receivedTimes()).hasSize(expectedTotal);
        List<Long> times = List.copyOf(permitTimes);
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
        assertThat(max).isLessThanOrEqualTo(CAP);
    }
}
