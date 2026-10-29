package com.mmrtr.lol.infra.riot.ratelimit;

import static com.mmrtr.lol.support.riot.RiotCallPriority.BACKGROUND;
import static com.mmrtr.lol.support.riot.RiotCallPriority.DEFERRED;
import static com.mmrtr.lol.support.riot.RiotCallPriority.USER;
import static org.assertj.core.api.Assertions.assertThat;

import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisRiotRateLimiterTest {

    /** 초 경계 + 500ms. 고정 시각이라 테스트가 초 경계에 걸치지 않는다. */
    private static final long NOW = 1_800_000_000_500L;
    private static final String ROUTING = "asia";
    private static final String METHOD = "m";

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

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
    void flush() {
        redisson.getKeys().flushall();
    }

    @Test
    @DisplayName("USER 는 이번 초 허용치(45)를 쓰고 다음 초를 예약한다")
    void user_grantsAllowanceThenReservesNextSecond() {
        RedisRiotRateLimiter limiter = limiter();

        assertGrantedNow(limiter, METHOD, USER, 45);
        assertThat(limiter.acquire(ROUTING, METHOD, USER)).isEqualTo(500);
    }

    @Test
    @DisplayName("배운 app 한도가 기본값을 덮어쓴다")
    void learnedAppLimit_overridesDefault() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.learnLimits(ROUTING, METHOD, "100:10", null);

        assertGrantedNow(limiter, METHOD, USER, 9);
        assertThat(limiter.acquire(ROUTING, METHOD, USER)).isPositive();
    }

    @Test
    @DisplayName("method 한도는 그 method 에만 적용된다")
    void methodLimit_appliesPerMethod() {
        RedisRiotRateLimiter limiter = limiter();
        // 200:10 → 1초 칸 18회
        limiter.learnLimits(ROUTING, METHOD, null, "200:10");

        assertGrantedNow(limiter, METHOD, USER, 18);
        assertThat(limiter.acquire(ROUTING, METHOD, USER)).isPositive();
        assertThat(limiter.acquire(ROUTING, "other", USER)).isZero();
    }

    @Test
    @DisplayName("1초에 1회도 안 되는 느린 창은 W초 칸으로 센다")
    void slowWindow_usesWholeWindowBucket() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.learnLimits(ROUTING, METHOD, "30:10,500:600", null);

        assertGrantedNow(limiter, METHOD, USER, 2);
        assertThat(limiter.acquire(ROUTING, METHOD, USER)).isEqualTo(500);
        // 600초 칸 하나(idx = 1_800_000_000 / 600)에 세 번 모두 쌓인다
        assertThat(counter("riot:rl:c:asia|app:600:3000000")).isEqualTo("3");
    }

    @Test
    @DisplayName("Lua 허용치는 RateLimitWindow 와 같다")
    void allowance_matchesRateLimitWindow() {
        for (String header : List.of(
                "500:10", "300:10", "100:10", "200:10", "20:10", "20:1", "30:10", "7:3", "500:600", "100:120")) {
            redisson.getKeys().flushall();
            RedisRiotRateLimiter limiter = limiter();
            limiter.learnLimits(ROUTING, METHOD, header, null);

            int granted = 0;
            while (limiter.acquire(ROUTING, METHOD, USER) == 0) {
                granted++;
            }

            assertThat(granted).as(header).isEqualTo(RateLimitWindow.parse(header).get(0).allowance(0.9));
        }
    }

    @Test
    @DisplayName("BACKGROUND 는 칸의 20% 를 USER 몫으로 남긴다")
    void background_leavesReserveForUser() {
        RedisRiotRateLimiter limiter = limiter();

        assertGrantedNow(limiter, METHOD, BACKGROUND, 36);
        assertThat(limiter.acquire(ROUTING, METHOD, BACKGROUND)).isEqualTo(RiotRateLimiter.YIELD);
        assertGrantedNow(limiter, METHOD, USER, 9);
    }

    @Test
    @DisplayName("USER 가 기다리는 동안 DEFERRED·BACKGROUND 는 양보한다")
    void lowerPriorities_yieldWhileUserWaits() {
        RedisRiotRateLimiter limiter = limiter();
        assertGrantedNow(limiter, METHOD, USER, 45);
        assertThat(limiter.acquire(ROUTING, METHOD, USER)).isPositive();

        assertThat(limiter.acquire(ROUTING, METHOD, DEFERRED)).isEqualTo(RiotRateLimiter.YIELD);
        assertThat(limiter.acquire(ROUTING, METHOD, BACKGROUND)).isEqualTo(RiotRateLimiter.YIELD);
    }

    @Test
    @DisplayName("BACKGROUND 는 최근 DEFERRED 요청에 양보한다")
    void background_yieldsToDeferredDemand() {
        RedisRiotRateLimiter limiter = limiter();

        assertThat(limiter.acquire(ROUTING, METHOD, DEFERRED)).isZero();
        assertThat(limiter.acquire(ROUTING, METHOD, BACKGROUND)).isEqualTo(RiotRateLimiter.YIELD);
    }

    @Test
    @DisplayName("routing 정지 중 USER 는 정지가 끝날 때까지 기다리고 나머지는 양보한다")
    void pause_delaysUserAndYieldsOthers() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.pause(ROUTING, null, Duration.ofSeconds(2));

        assertThat(limiter.acquire(ROUTING, METHOD, USER)).isGreaterThanOrEqualTo(2000);
        assertThat(limiter.acquire(ROUTING, METHOD, BACKGROUND)).isEqualTo(RiotRateLimiter.YIELD);
    }

    @Test
    @DisplayName("method 정지는 다른 method 를 막지 않는다")
    void methodPause_leavesOtherMethodsAlone() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.pause(ROUTING, METHOD, Duration.ofSeconds(2));

        assertThat(limiter.acquire(ROUTING, METHOD, USER)).isGreaterThanOrEqualTo(2000);
        assertThat(limiter.acquire(ROUTING, "other", USER)).isZero();
    }

    @Test
    @DisplayName("짧은 정지가 더 긴 정지를 줄이지 않는다")
    void pause_keepsLongerPause() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.pause(ROUTING, null, Duration.ofSeconds(3));
        limiter.pause(ROUTING, null, Duration.ofSeconds(1));

        assertThat(limiter.acquire(ROUTING, METHOD, USER)).isGreaterThanOrEqualTo(3000);
    }

    @Test
    @DisplayName("USER 는 예약 범위(lookahead)를 넘으면 NO_CAPACITY")
    void user_noCapacityBeyondLookahead() {
        RiotAPIProperties properties = new RiotAPIProperties();
        properties.getRateLimit().setLookaheadSeconds(2);
        RedisRiotRateLimiter limiter = limiter(properties);

        for (int i = 0; i < 45 * 3; i++) {
            assertThat(limiter.acquire(ROUTING, METHOD, USER)).as("%d번째", i + 1).isNotNegative();
        }
        assertThat(limiter.acquire(ROUTING, METHOD, USER)).isEqualTo(RiotRateLimiter.NO_CAPACITY);
    }

    @Test
    @DisplayName("새 인스턴스는 카운터를 초기화하지 않는다")
    void countersSurviveNewInstance() {
        assertGrantedNow(limiter(), METHOD, USER, 45);

        assertThat(limiter().acquire(ROUTING, METHOD, USER)).isEqualTo(500);
    }

    @Test
    @DisplayName("backlogMillis 는 USER 가 예약한 가장 먼 초까지 남은 시간")
    void backlogMillis_reflectsReservation() {
        RedisRiotRateLimiter limiter = limiter();
        assertThat(limiter.backlogMillis(ROUTING)).isZero();

        assertGrantedNow(limiter, METHOD, USER, 45);
        limiter.acquire(ROUTING, METHOD, USER);

        assertThat(limiter.backlogMillis(ROUTING)).isEqualTo(500);
    }

    @Test
    @DisplayName("userCallsPerSecond 는 기본 한도, 배운 뒤엔 배운 한도로 계산한다")
    void userCallsPerSecond_defaultAndLearned() {
        RedisRiotRateLimiter limiter = limiter();
        assertThat(limiter.userCallsPerSecond(ROUTING)).isEqualTo(45);

        limiter.learnLimits(ROUTING, METHOD, "300:10", null);

        assertThat(limiter.userCallsPerSecond(ROUTING)).isEqualTo(27);
    }

    @Test
    @DisplayName("빈 헤더·잘못된 헤더는 무시하고 이전 한도를 유지한다")
    void learnLimits_ignoresBlankHeader() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.learnLimits(ROUTING, METHOD, "100:10", null);
        limiter.learnLimits(ROUTING, METHOD, "", "");
        limiter.learnLimits(ROUTING, METHOD, null, null);
        limiter.learnLimits(ROUTING, METHOD, "500", "500:0");

        assertGrantedNow(limiter, METHOD, USER, 9);
        assertThat(limiter.acquire(ROUTING, METHOD, USER)).isPositive();
    }

    @Test
    @DisplayName("마지막으로 본 한도와 같으면 Redis 에 다시 쓰지 않는다")
    void learnLimits_skipsWriteWhenUnchanged() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.learnLimits(ROUTING, METHOD, "100:10", null);
        redisson.getMap("riot:rl:limits", StringCodec.INSTANCE).delete();

        limiter.learnLimits(ROUTING, METHOD, "100:10", null);

        assertThat(redisson.getMap("riot:rl:limits", StringCodec.INSTANCE).isExists()).isFalse();
    }

    @Test
    @DisplayName("Redis 스크립트 캐시가 비워지면 다시 올리고 호출한다")
    void scriptCacheFlushed_reloadsScript() {
        RedisRiotRateLimiter limiter = limiter();
        assertThat(limiter.acquire(ROUTING, METHOD, USER)).isZero();

        redisson.getScript().scriptFlush();

        assertThat(limiter.acquire(ROUTING, METHOD, USER)).isZero();
        assertThat(counter("riot:rl:c:asia|app:10:1800000000")).isEqualTo("2");

        limiter.pause(ROUTING, null, Duration.ofSeconds(1));
        redisson.getScript().scriptFlush();
        limiter.pause(ROUTING, null, Duration.ofSeconds(2));
        assertThat(limiter.acquire(ROUTING, METHOD, USER)).isGreaterThanOrEqualTo(2000);
    }

    @Test
    @DisplayName("시계를 주지 않으면 Redis TIME 으로 센다")
    void redisClock_whenNoOverride() {
        RedisRiotRateLimiter limiter = new RedisRiotRateLimiter(redisson, new RiotAPIProperties());
        assertThat(limiter.acquire(ROUTING, METHOD, USER)).isZero();

        limiter.pause(ROUTING, null, Duration.ofSeconds(2));

        assertThat(limiter.acquire(ROUTING, METHOD, USER)).isBetween(1500L, 2000L);
    }

    private static RedisRiotRateLimiter limiter() {
        return limiter(new RiotAPIProperties());
    }

    private static RedisRiotRateLimiter limiter(RiotAPIProperties properties) {
        return new RedisRiotRateLimiter(redisson, properties, () -> NOW);
    }

    private static String counter(String key) {
        return redisson.<String>getBucket(key, StringCodec.INSTANCE).get();
    }

    private static void assertGrantedNow(
            RiotRateLimiter limiter, String method, RiotCallPriority priority, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(limiter.acquire(ROUTING, method, priority)).as("%d번째", i + 1).isZero();
        }
    }
}
