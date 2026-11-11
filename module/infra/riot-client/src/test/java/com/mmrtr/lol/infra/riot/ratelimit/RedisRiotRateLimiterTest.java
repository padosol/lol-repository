package com.mmrtr.lol.infra.riot.ratelimit;

import static com.mmrtr.lol.support.riot.RiotCallPriority.BACKGROUND;
import static com.mmrtr.lol.support.riot.RiotCallPriority.DEFERRED;
import static com.mmrtr.lol.support.riot.RiotCallPriority.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermit;
import com.mmrtr.lol.support.riot.RiotPermits;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 단건 허가로 본 한도 규칙·정지. 한도는 설정에서 읽고, 기본 app-limits "500:10" 이면 app 규칙 상한은 floor(500 × 0.9) = 450 이다.
 * platform KR 의 match 호출은 routing asia 다. 묶음 허가는 {@link RedisRiotRateLimiterPermitTest} 가 본다.
 */
@Testcontainers
class RedisRiotRateLimiterTest {

    private static final long NOW = 1_800_000_000_500L;
    private static final String PLATFORM = "KR";
    private static final String ROUTING = "asia";
    private static final RiotMethod METHOD = RiotMethod.MATCH;
    private static final RiotMethod OTHER = RiotMethod.MATCH_TIMELINE;
    private static final String APP_10 = RedisRiotRateLimiter.ruleKey("asia|app", 10);
    private static final String BG_1 = RedisRiotRateLimiter.ruleKey("asia|bg", 1);

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static RedissonClient redisson;

    /** 테스트 시계. 정지·유저 활동처럼 "끝나는 시각" 값으로 판정하는 것도 이 시계를 따른다. */
    private final AtomicLong clock = new AtomicLong(NOW);

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
    @DisplayName("비어 있으면 상한(450)까지 허가하고 다음은 거절한다")
    void burst_grantsUpToCapThenDenies() {
        RedisRiotRateLimiter limiter = limiter();

        assertGranted(limiter, USER, 450);
        assertThat(acquire(limiter, USER)).isEmpty();
    }

    @Test
    @DisplayName("창이 지나면 다시 허가한다")
    void windowElapses_thenGranted() {
        RedisRiotRateLimiter limiter = limiter();
        assertGranted(limiter, USER, 450);

        clock.addAndGet(10_000);

        assertThat(acquire(limiter, USER)).isPresent();
    }

    @Test
    @DisplayName("method 규칙이 막으면 app 규칙에도 기록하지 않는다")
    void methodRule_blocksAndNothingRecorded() {
        // 2:10 → method 상한 1
        RedisRiotRateLimiter limiter = limiter(rateLimit -> rateLimit.setMethodLimits(Map.of(METHOD.methodName(), "2:10")));

        assertThat(acquire(limiter, USER)).isPresent();
        assertThat(acquire(limiter, USER)).isEmpty();
        assertThat(zset(APP_10).size()).isEqualTo(1);
    }

    @Test
    @DisplayName("창이 둘(10초·600초)이면 둘 다 본다")
    void twoWindows_bothChecked() {
        // 상한 90 (10초), 135 (600초)
        RedisRiotRateLimiter limiter = limiter(rateLimit -> rateLimit.setAppLimits("100:10,150:600"));
        assertGranted(limiter, USER, 90);

        clock.addAndGet(10_000);
        assertGranted(limiter, USER, 45);

        assertThat(acquire(limiter, USER)).isEmpty();
    }

    @Test
    @DisplayName("유저 활동 중 BACKGROUND 는 상한의 절반에서 멈추고 USER 는 끝까지 간다")
    void userActive_backgroundStopsAtHalf_userGoesToFull() {
        RedisRiotRateLimiter limiter = limiter(rateLimit -> rateLimit.setBackgroundMaxPerSecond(1000));
        assertGranted(limiter, USER, 1);

        assertGranted(limiter, BACKGROUND, 224);
        assertThat(acquire(limiter, BACKGROUND)).isEmpty();

        assertGranted(limiter, USER, 225);
        assertThat(acquire(limiter, USER)).isEmpty();
    }

    @Test
    @DisplayName("낮춤 비율이 1 을 넘으면 1 로 잘라 BACKGROUND 도 상한(450)에서 멈춘다")
    void ceilingRatioAboveOne_isClampedToFullCap() {
        RedisRiotRateLimiter limiter = limiter(rateLimit -> {
            rateLimit.setBackgroundCeilingRatio(2.0);
            rateLimit.setBackgroundMaxPerSecond(1000);
        });
        assertGranted(limiter, USER, 1);

        assertGranted(limiter, BACKGROUND, 449);
        assertThat(acquire(limiter, BACKGROUND)).isEmpty();
        assertThat(zset(APP_10).size()).isEqualTo(450);
    }

    @Test
    @DisplayName("유저 활동 표시가 끝나면 BACKGROUND 는 절반을 넘어 간다")
    void userIdle_backgroundPassesHalf() {
        RedisRiotRateLimiter limiter = limiter(rateLimit -> rateLimit.setBackgroundMaxPerSecond(1000));
        assertGranted(limiter, USER, 1);

        clock.addAndGet(10_001);

        assertGranted(limiter, BACKGROUND, 449);
    }

    @Test
    @DisplayName("BACKGROUND 는 한가할 때도 1초에 40회까지다. USER·DEFERRED 는 백필 규칙에 막히지 않는다")
    void backgroundRule_fortyPerSecondEvenWhenIdle() {
        RedisRiotRateLimiter limiter = limiter();

        assertGranted(limiter, BACKGROUND, 40);
        assertThat(acquire(limiter, BACKGROUND)).isEmpty();
        assertThat(acquire(limiter, USER)).isPresent();
        assertThat(acquire(limiter, DEFERRED)).isPresent();
        assertThat(zset(BG_1).size()).isEqualTo(40);

        clock.addAndGet(1_000);
        assertThat(acquire(limiter, BACKGROUND)).isPresent();
    }

    @Test
    @DisplayName("background-max-per-second 가 0 이면 백필 규칙을 두지 않는다")
    void backgroundMaxPerSecondZero_disablesRule() {
        RedisRiotRateLimiter limiter = limiter(rateLimit -> rateLimit.setBackgroundMaxPerSecond(0));

        assertGranted(limiter, BACKGROUND, 100);
        assertThat(zset(BG_1).isExists()).isFalse();
    }

    @Test
    @DisplayName("낮춘 상한은 최소 1 이라 창이 비면 BACKGROUND 도 1건은 나간다")
    void tinyLimit_loweredCapIsAtLeastOne() {
        // 2:10 → 상한 1. 유저 활동 중이면 floor(1 × 0.5) = 0 → 1
        RedisRiotRateLimiter limiter = limiter(rateLimit -> {
            rateLimit.setAppLimits("2:10");
            rateLimit.setUserActiveWindow(Duration.ofSeconds(20));
        });
        assertGranted(limiter, USER, 1);

        // 유저 활동 중(20초)이고 10초 창은 비었다
        clock.addAndGet(10_000);

        assertThat(acquire(limiter, BACKGROUND)).isPresent();
    }

    @Test
    @DisplayName("routing 정지 중이면 모든 우선순위를 거절하고 아무것도 기록하지 않는다")
    void pause_deniesAllPriorities() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.pause(ROUTING, null, Duration.ofSeconds(2));

        assertThat(acquire(limiter, USER)).isEmpty();
        assertThat(acquire(limiter, DEFERRED)).isEmpty();
        assertThat(acquire(limiter, BACKGROUND)).isEmpty();
        assertThat(zset(APP_10).isExists()).isFalse();
    }

    @Test
    @DisplayName("method 정지는 다른 method 를 막지 않는다")
    void methodPause_doesNotBlockOtherMethod() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.pause(ROUTING, METHOD.methodName(), Duration.ofSeconds(2));

        assertThat(acquire(limiter, USER)).isEmpty();
        assertThat(limiter.tryAcquire(PLATFORM, USER, Map.of(OTHER, 1))).isPresent();
    }

    @Test
    @DisplayName("짧은 정지가 더 긴 정지를 줄이지 않는다")
    void shorterPause_doesNotShortenLonger() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.pause(ROUTING, null, Duration.ofSeconds(3));
        limiter.pause(ROUTING, null, Duration.ofSeconds(1));

        clock.addAndGet(2_000);
        assertThat(acquire(limiter, USER)).isEmpty();

        clock.addAndGet(1_001);
        assertThat(acquire(limiter, USER)).isPresent();
    }

    @Test
    @DisplayName("모르는 platform 이면 허가가 IllegalArgumentException")
    void unknownPlatform_isIllegalArgument() {
        RedisRiotRateLimiter limiter = limiter();

        assertThatThrownBy(() -> limiter.tryAcquire("XX", USER, Map.of(METHOD, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("method 한도는 설정에서 읽고 routing 마다 따로 센다")
    void methodLimitFromConfig_countedPerRouting() {
        // 4:10 → method 상한 3
        RedisRiotRateLimiter limiter = limiter(rateLimit -> rateLimit.setMethodLimits(Map.of(METHOD.methodName(), "4:10")));

        assertGranted(limiter, USER, 3);
        assertThat(acquire(limiter, USER)).isEmpty();
        // NA 의 match 는 americas 라 따로 센다
        assertThat(limiter.tryAcquire("NA", USER, Map.of(METHOD, 1))).isPresent();
        assertThat(zset(RedisRiotRateLimiter.ruleKey("americas|m|match-v5.match", 10)).size()).isEqualTo(1);
    }

    @Test
    @DisplayName("kr 의 app 창이 차도 jp1 은 허가된다")
    void appCountedPerRouting_krFullDoesNotBlockJp() {
        RScoredSortedSet<String> krApp = zset(RedisRiotRateLimiter.ruleKey("kr|app", 10));
        for (int i = 0; i < 450; i++) {
            krApp.add(NOW, "pre-" + i);
        }
        RedisRiotRateLimiter limiter = limiter();

        assertThat(limiter.tryAcquire("KR", USER, Map.of(RiotMethod.SUMMONER_BY_PUUID, 1))).isEmpty();
        assertThat(limiter.tryAcquire("JP", USER, Map.of(RiotMethod.SUMMONER_BY_PUUID, 1))).isPresent();
    }

    @Test
    @DisplayName("한 묶음에서 한도가 있는 method 만 method 규칙을 갖는다")
    void mixedBatch_onlyConfiguredMethodHasMethodRule() {
        RedisRiotRateLimiter limiter = limiter(rateLimit -> rateLimit.setMethodLimits(Map.of(METHOD.methodName(), "4:10")));

        assertThat(limiter.tryAcquire(PLATFORM, USER, Map.of(METHOD, 3, OTHER, 5))).isPresent();

        assertThat(zset(RedisRiotRateLimiter.ruleKey("asia|m|match-v5.match", 10)).size()).isEqualTo(3);
        assertThat(zset(RedisRiotRateLimiter.ruleKey("asia|m|match-v5.timeline", 10)).isExists()).isFalse();
        assertThat(zset(APP_10).size()).isEqualTo(8);
    }

    @Test
    @DisplayName("새 인스턴스는 ZSET 을 지우지 않는다")
    void newInstance_doesNotClearZsets() {
        assertGranted(limiter(), USER, 450);

        assertThat(acquire(limiter(), USER)).isEmpty();
    }

    @Test
    @DisplayName("method 한도는 그 method 에만 적용된다")
    void methodLimit_appliesPerMethod() {
        // 200:10 → method 상한 180
        RedisRiotRateLimiter limiter = limiter(rateLimit -> rateLimit.setMethodLimits(Map.of(METHOD.methodName(), "200:10")));

        assertGranted(limiter, USER, 180);
        assertThat(acquire(limiter, USER)).isEmpty();
        assertThat(limiter.tryAcquire(PLATFORM, USER, Map.of(OTHER, 1))).isPresent();
    }

    @Test
    @DisplayName("Redis 스크립트 캐시가 비워지면 다시 올리고 호출한다")
    void scriptCacheFlushed_reloadsScript() {
        RedisRiotRateLimiter limiter = limiter();
        assertThat(acquire(limiter, USER)).isPresent();

        redisson.getScript().scriptFlush();

        assertThat(acquire(limiter, USER)).isPresent();
        assertThat(zset(APP_10).size()).isEqualTo(2);

        limiter.pause(ROUTING, null, Duration.ofSeconds(1));
        redisson.getScript().scriptFlush();
        limiter.pause(ROUTING, null, Duration.ofSeconds(2));
        clock.addAndGet(1_500);
        assertThat(acquire(limiter, USER)).isEmpty();
    }

    @Test
    @DisplayName("시계를 주지 않으면 Redis TIME 으로 센다")
    void redisClock_whenNoOverride() {
        RedisRiotRateLimiter limiter = new RedisRiotRateLimiter(redisson, new RiotAPIProperties());
        long before = System.currentTimeMillis();
        RiotPermit permit = limiter.tryAcquire(PLATFORM, USER, Map.of(METHOD, 1)).orElseThrow().take(METHOD);

        assertThat(zset(APP_10).getScore(permit.token())).isBetween((double) before - 5_000, (double) before + 5_000);

        limiter.pause(ROUTING, null, Duration.ofSeconds(2));

        assertThat(limiter.tryAcquire(PLATFORM, USER, Map.of(METHOD, 1))).isEmpty();
    }

    private RedisRiotRateLimiter limiter() {
        return limiter(rateLimit -> { });
    }

    /** 기본 설정 위에 tweak 을 적용하고 테스트 시계를 넘긴다. */
    private RedisRiotRateLimiter limiter(Consumer<RiotAPIProperties.RateLimit> tweak) {
        RiotAPIProperties properties = new RiotAPIProperties();
        tweak.accept(properties.getRateLimit());
        return new RedisRiotRateLimiter(redisson, properties, clock::get);
    }

    private static Optional<RiotPermits> acquire(RedisRiotRateLimiter limiter, RiotCallPriority priority) {
        return limiter.tryAcquire(PLATFORM, priority, Map.of(METHOD, 1));
    }

    private static void assertGranted(RedisRiotRateLimiter limiter, RiotCallPriority priority, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(acquire(limiter, priority)).as("%s %d번째", priority, i + 1).isPresent();
        }
    }

    private static RScoredSortedSet<String> zset(String key) {
        return redisson.getScoredSortedSet(key, StringCodec.INSTANCE);
    }
}
