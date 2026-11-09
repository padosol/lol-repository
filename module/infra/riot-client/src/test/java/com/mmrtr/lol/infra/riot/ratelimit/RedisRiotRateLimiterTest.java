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
import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 단건 허가로 본 한도 규칙·학습·정지·기록 옮기기. 기본 app-default "500:10" 이면 app 규칙 상한은 floor(500 × 0.9) = 450 이다.
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
        RedisRiotRateLimiter limiter = limiter();
        // 2:10 → method 상한 1
        limiter.learnLimits(ROUTING, METHOD.methodName(), null, "2:10");

        assertThat(acquire(limiter, USER)).isPresent();
        assertThat(acquire(limiter, USER)).isEmpty();
        assertThat(zset(APP_10).size()).isEqualTo(1);
    }

    @Test
    @DisplayName("창이 둘(10초·600초)이면 둘 다 본다")
    void twoWindows_bothChecked() {
        // 상한 90 (10초), 135 (600초)
        RedisRiotRateLimiter limiter = limiter(rateLimit -> rateLimit.setAppDefault("100:10,150:600"));
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
            rateLimit.setAppDefault("2:10");
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
    @DisplayName("실행 중 새 창을 배우면 빈 새 창 ZSET 과 기존 ZSET 에 모두 기록한다")
    void learnedNewWindow_recordsInBothKeys() {
        RedisRiotRateLimiter limiter = limiter();
        assertThat(acquire(limiter, USER)).isPresent();

        limiter.learnLimits(ROUTING, METHOD.methodName(), "500:10,30000:600", null);

        String token = acquire(limiter, USER).orElseThrow().take(METHOD).token();
        assertThat(zset(APP_10).getScore(token)).isEqualTo((double) NOW);
        assertThat(zset(RedisRiotRateLimiter.ruleKey("asia|app", 600)).getScore(token)).isEqualTo((double) NOW);
    }

    @Test
    @DisplayName("complete 는 그 토큰의 기록을 지금 시각으로 옮기고, 없는 토큰은 건드리지 않는다")
    void complete_movesRecordToNow() {
        RedisRiotRateLimiter limiter = limiter();
        RiotPermit permit = acquire(limiter, USER).orElseThrow().take(METHOD);

        clock.addAndGet(300);
        limiter.complete(permit);

        assertThat(zset(APP_10).getScore(permit.token())).isEqualTo((double) (NOW + 300));

        limiter.complete(new RiotPermit("nope", PLATFORM, METHOD, USER));

        assertThat(zset(APP_10).size()).isEqualTo(1);
        assertThat(zset(APP_10).getScore("nope")).isNull();
    }

    @Test
    @DisplayName("BACKGROUND 의 complete 는 app 규칙 기록만 옮기고 백필 규칙 기록은 허가 시각에 둔다")
    void complete_doesNotMoveBackgroundRule() {
        RedisRiotRateLimiter limiter = limiter();
        RiotPermit permit = acquire(limiter, BACKGROUND).orElseThrow().take(METHOD);

        clock.addAndGet(300);
        limiter.complete(permit);

        assertThat(zset(BG_1).getScore(permit.token())).isEqualTo((double) NOW);
        assertThat(zset(APP_10).getScore(permit.token())).isEqualTo((double) (NOW + 300));
    }

    @Test
    @DisplayName("complete 로 옮긴 기록은 응답 지연만큼 늦게 창을 빠진다")
    void complete_delaysNextSlotByLatency() {
        // 2:10 → 상한 1
        RedisRiotRateLimiter limiter = limiter(rateLimit -> rateLimit.setAppDefault("2:10"));
        RiotPermit permit = acquire(limiter, USER).orElseThrow().take(METHOD);
        clock.addAndGet(300);
        limiter.complete(permit);

        clock.set(NOW + 10_000);
        assertThat(acquire(limiter, USER)).isEmpty();

        clock.set(NOW + 10_300);
        assertThat(acquire(limiter, USER)).isPresent();
    }

    @Test
    @DisplayName("모르는 platform 이면 허가·complete 모두 IllegalArgumentException")
    void unknownPlatform_isIllegalArgument() {
        RedisRiotRateLimiter limiter = limiter();

        assertThatThrownBy(() -> limiter.tryAcquire("XX", USER, Map.of(METHOD, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.complete(new RiotPermit("t", "XX", METHOD, USER)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("다른 인스턴스가 배운 한도는 limits-refresh 가 지난 뒤 읽는다")
    void otherInstanceLearnedLimit_readAfterRefresh() {
        RedisRiotRateLimiter a = limiter();
        RedisRiotRateLimiter b = limiter();
        // B 가 한도 캐시를 채운다 (배운 값 없음 → 기본값, 상한 450)
        assertGranted(b, USER, 1);
        a.learnLimits(ROUTING, METHOD.methodName(), "100:10", null);

        clock.addAndGet(1);
        // B 는 아직 상한 450 으로 센다
        assertGranted(b, USER, 100);

        // 캐시 갱신 주기(10초)가 지났고, 앞 기록 101건은 10초 창을 빠졌다
        clock.set(NOW + 10_001);
        assertGranted(b, USER, 90);
        assertThat(acquire(b, USER)).isEmpty();
    }

    @Test
    @DisplayName("새 인스턴스는 ZSET 을 지우지 않는다")
    void newInstance_doesNotClearZsets() {
        assertGranted(limiter(), USER, 450);

        assertThat(acquire(limiter(), USER)).isEmpty();
    }

    @Test
    @DisplayName("배운 app 한도가 기본값을 덮어쓴다")
    void learnedAppLimit_overridesDefault() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.learnLimits(ROUTING, METHOD.methodName(), "100:10", null);

        assertGranted(limiter, USER, 90);
        assertThat(acquire(limiter, USER)).isEmpty();
    }

    @Test
    @DisplayName("method 한도는 그 method 에만 적용된다")
    void methodLimit_appliesPerMethod() {
        RedisRiotRateLimiter limiter = limiter();
        // 200:10 → method 상한 180
        limiter.learnLimits(ROUTING, METHOD.methodName(), null, "200:10");

        assertGranted(limiter, USER, 180);
        assertThat(acquire(limiter, USER)).isEmpty();
        assertThat(limiter.tryAcquire(PLATFORM, USER, Map.of(OTHER, 1))).isPresent();
    }

    @Test
    @DisplayName("빈 헤더·잘못된 헤더는 무시하고 이전 한도를 유지한다")
    void learnLimits_ignoresBlankHeader() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.learnLimits(ROUTING, METHOD.methodName(), "100:10", null);
        limiter.learnLimits(ROUTING, METHOD.methodName(), "", "");
        limiter.learnLimits(ROUTING, METHOD.methodName(), null, null);
        limiter.learnLimits(ROUTING, METHOD.methodName(), "500", "500:0");

        assertGranted(limiter, USER, 90);
        assertThat(acquire(limiter, USER)).isEmpty();
    }

    @Test
    @DisplayName("같은 한도는 30초 안에는 다시 쓰지 않고, 30초가 지나면 다시 써서 잃어버린 한도를 복구한다")
    void learnLimits_rewritesUnchangedLimitAfterRefreshInterval() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.learnLimits(ROUTING, METHOD.methodName(), "100:10", null);
        limitsHash().delete();

        clock.addAndGet(29_999);
        limiter.learnLimits(ROUTING, METHOD.methodName(), "100:10", null);
        assertThat(limitsHash().isExists()).isFalse();

        clock.addAndGet(2);
        limiter.learnLimits(ROUTING, METHOD.methodName(), "100:10", null);
        assertThat(limitsHash().get("asia|app")).isEqualTo("100:10");
    }

    @Test
    @DisplayName("Redis 스크립트 캐시가 비워지면 다시 올리고 호출한다")
    void scriptCacheFlushed_reloadsScript() {
        RedisRiotRateLimiter limiter = limiter();
        RiotPermit first = acquire(limiter, USER).orElseThrow().take(METHOD);
        limiter.complete(first);

        redisson.getScript().scriptFlush();

        assertThat(acquire(limiter, USER)).isPresent();
        assertThat(zset(APP_10).size()).isEqualTo(2);
        clock.addAndGet(300);
        limiter.complete(first);
        assertThat(zset(APP_10).getScore(first.token())).isEqualTo((double) (NOW + 300));

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

    private static RMap<String, String> limitsHash() {
        return redisson.getMap("riot:rl:limits", StringCodec.INSTANCE);
    }
}
