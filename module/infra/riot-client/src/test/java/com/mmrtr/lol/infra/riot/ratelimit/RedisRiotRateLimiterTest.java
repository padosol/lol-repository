package com.mmrtr.lol.infra.riot.ratelimit;

import static com.mmrtr.lol.support.riot.RiotCallPriority.BACKGROUND;
import static com.mmrtr.lol.support.riot.RiotCallPriority.DEFERRED;
import static com.mmrtr.lol.support.riot.RiotCallPriority.USER;
import static org.assertj.core.api.Assertions.assertThat;

import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import java.time.Duration;
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

/** 기본 app-default "500:10" 이면 app 규칙 상한은 floor(500 × 0.9) = 450 이다. */
@Testcontainers
class RedisRiotRateLimiterTest {

    private static final long NOW = 1_800_000_000_500L;
    private static final String ROUTING = "asia";
    private static final String METHOD = "m";
    private static final String APP_10 = RedisRiotRateLimiter.ruleKey("asia|app", 10);
    private static final String BG_1 = RedisRiotRateLimiter.ruleKey("asia|bg", 1);

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static RedissonClient redisson;

    /** 테스트 시계. 정지·유저 활동처럼 "끝나는 시각" 값으로 판정하는 것도 이 시계를 따른다. */
    private final AtomicLong clock = new AtomicLong(NOW);
    private int seq;

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
    @DisplayName("비어 있으면 상한(450)까지 한꺼번에 허가하고, 다음은 창이 빌 때까지 기다린다")
    void burst_grantsUpToCapAtOnce() {
        RedisRiotRateLimiter limiter = limiter();

        assertGranted(limiter, USER, 450);
        assertThat(acquire(limiter, USER)).isEqualTo(10_000);
    }

    @Test
    @DisplayName("거절된 호출은 기록하지 않는다")
    void rejected_isNotRecorded() {
        RedisRiotRateLimiter limiter = limiter();
        assertGranted(limiter, USER, 450);

        assertThat(limiter.acquire("x", ROUTING, METHOD, USER)).isPositive();

        assertThat(zset(APP_10).size()).isEqualTo(450);
        assertThat(zset(APP_10).getScore("x")).isNull();
    }

    @Test
    @DisplayName("돌려준 대기가 지나면 허가한다")
    void waitElapses_thenGranted() {
        RedisRiotRateLimiter limiter = limiter();
        assertGranted(limiter, USER, 450);

        clock.addAndGet(10_000);

        assertThat(acquire(limiter, USER)).isZero();
    }

    @Test
    @DisplayName("method 규칙이 막으면 app 규칙에도 기록하지 않는다")
    void methodRule_blocksAndNothingRecorded() {
        RedisRiotRateLimiter limiter = limiter();
        // 2:10 → method 상한 1
        limiter.learnLimits(ROUTING, METHOD, null, "2:10");

        assertThat(acquire(limiter, USER)).isZero();
        assertThat(acquire(limiter, USER)).isPositive();
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

        assertThat(acquire(limiter, USER)).isEqualTo(590_000);
    }

    @Test
    @DisplayName("개수가 상한보다 많으면 인덱스 n − 상한 항목이 창을 빠지는 시각까지 기다린다")
    void waitUsesIndexNMinusCap() {
        RedisRiotRateLimiter limiter = limiter(rateLimit -> rateLimit.setBackgroundMaxPerSecond(1000));
        assertGranted(limiter, USER, 50);
        clock.addAndGet(1000);
        assertGranted(limiter, USER, 250);

        // 유저 활동 중 BACKGROUND 상한 225, n = 300 → 인덱스 75 는 NOW + 1000 에 허가된 항목이다
        // (가장 오래된 항목 기준이면 9_000)
        assertThat(acquire(limiter, BACKGROUND)).isEqualTo(10_000);
    }

    @Test
    @DisplayName("유저 활동 중 BACKGROUND 는 상한의 절반에서 멈추고 USER 는 끝까지 간다")
    void userActive_backgroundStopsAtHalf_userGoesToFull() {
        RedisRiotRateLimiter limiter = limiter(rateLimit -> rateLimit.setBackgroundMaxPerSecond(1000));
        assertGranted(limiter, USER, 1);

        assertGranted(limiter, BACKGROUND, 224);
        assertThat(acquire(limiter, BACKGROUND)).isPositive();

        assertGranted(limiter, USER, 225);
        assertThat(acquire(limiter, USER)).isPositive();
    }

    @Test
    @DisplayName("유저 활동 중 DEFERRED 는 상한의 80%(360) 에서 멈춘다")
    void deferred_stopsAtEightyPercentWhenUserActive() {
        RedisRiotRateLimiter limiter = limiter();
        assertGranted(limiter, USER, 1);

        assertGranted(limiter, DEFERRED, 359);
        assertThat(acquire(limiter, DEFERRED)).isPositive();
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
        assertThat(acquire(limiter, BACKGROUND)).isEqualTo(1_000);
        assertThat(acquire(limiter, USER)).isZero();
        assertThat(acquire(limiter, DEFERRED)).isZero();
        assertThat(zset(BG_1).size()).isEqualTo(40);
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

        assertThat(acquire(limiter, BACKGROUND)).isZero();
    }

    @Test
    @DisplayName("routing 정지 중이면 모든 우선순위가 남은 정지 시간을 받고 아무것도 기록하지 않는다")
    void pause_returnsRemainingForAllPriorities() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.pause(ROUTING, null, Duration.ofSeconds(2));

        assertThat(acquire(limiter, USER)).isEqualTo(2_000);
        assertThat(acquire(limiter, DEFERRED)).isEqualTo(2_000);
        assertThat(acquire(limiter, BACKGROUND)).isEqualTo(2_000);
        assertThat(zset(APP_10).isExists()).isFalse();
    }

    @Test
    @DisplayName("method 정지는 다른 method 를 막지 않는다")
    void methodPause_doesNotBlockOtherMethod() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.pause(ROUTING, METHOD, Duration.ofSeconds(2));

        assertThat(limiter.acquire(nextId(), ROUTING, METHOD, USER)).isPositive();
        assertThat(limiter.acquire(nextId(), ROUTING, "other", USER)).isZero();
    }

    @Test
    @DisplayName("짧은 정지가 더 긴 정지를 줄이지 않는다")
    void shorterPause_doesNotShortenLonger() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.pause(ROUTING, null, Duration.ofSeconds(3));
        limiter.pause(ROUTING, null, Duration.ofSeconds(1));

        assertThat(acquire(limiter, USER)).isGreaterThanOrEqualTo(3000);
    }

    @Test
    @DisplayName("실행 중 새 창을 배우면 빈 새 창 ZSET 과 기존 ZSET 에 모두 기록한다")
    void learnedNewWindow_recordsInBothKeys() {
        RedisRiotRateLimiter limiter = limiter();
        assertThat(limiter.acquire("e1", ROUTING, METHOD, USER)).isZero();

        limiter.learnLimits(ROUTING, METHOD, "500:10,30000:600", null);

        assertThat(limiter.acquire("e2", ROUTING, METHOD, USER)).isZero();
        assertThat(zset(APP_10).getScore("e2")).isEqualTo((double) NOW);
        assertThat(zset(RedisRiotRateLimiter.ruleKey("asia|app", 600)).getScore("e2")).isEqualTo((double) NOW);
    }

    @Test
    @DisplayName("complete 는 그 eventId 의 기록을 지금 시각으로 옮기고, 없는 eventId 는 건드리지 않는다")
    void complete_movesRecordToNow() {
        RedisRiotRateLimiter limiter = limiter();
        assertThat(limiter.acquire("e1", ROUTING, METHOD, USER)).isZero();

        clock.addAndGet(300);
        limiter.complete("e1", ROUTING, METHOD, USER);

        assertThat(zset(APP_10).getScore("e1")).isEqualTo((double) (NOW + 300));

        limiter.complete("nope", ROUTING, METHOD, USER);

        assertThat(zset(APP_10).size()).isEqualTo(1);
        assertThat(zset(APP_10).getScore("nope")).isNull();
    }

    @Test
    @DisplayName("BACKGROUND 의 complete 는 백필 규칙의 기록도 옮긴다")
    void complete_backgroundMovesBgRuleToo() {
        RedisRiotRateLimiter limiter = limiter();
        assertThat(limiter.acquire("b1", ROUTING, METHOD, BACKGROUND)).isZero();

        clock.addAndGet(300);
        limiter.complete("b1", ROUTING, METHOD, BACKGROUND);

        assertThat(zset(BG_1).getScore("b1")).isEqualTo((double) (NOW + 300));
    }

    @Test
    @DisplayName("complete 로 옮긴 기록은 응답 지연만큼 늦게 창을 빠진다")
    void complete_delaysNextSlotByLatency() {
        // 2:10 → 상한 1
        RedisRiotRateLimiter limiter = limiter(rateLimit -> rateLimit.setAppDefault("2:10"));
        assertThat(limiter.acquire("e1", ROUTING, METHOD, USER)).isZero();
        clock.addAndGet(300);
        limiter.complete("e1", ROUTING, METHOD, USER);

        clock.set(NOW + 10_000);

        assertThat(limiter.acquire("e2", ROUTING, METHOD, USER)).isEqualTo(300);
    }

    @Test
    @DisplayName("다른 인스턴스가 배운 한도는 limits-refresh 가 지난 뒤 읽는다")
    void otherInstanceLearnedLimit_readAfterRefresh() {
        RedisRiotRateLimiter a = limiter();
        RedisRiotRateLimiter b = limiter();
        // B 가 한도 캐시를 채운다 (배운 값 없음 → 기본값, 상한 450)
        assertGranted(b, USER, 1);
        a.learnLimits(ROUTING, METHOD, "100:10", null);

        clock.addAndGet(1);
        // B 는 아직 상한 450 으로 센다
        assertGranted(b, USER, 100);

        // 캐시 갱신 주기(10초)가 지났고, 앞 기록 101건은 10초 창을 빠졌다
        clock.set(NOW + 10_001);
        assertGranted(b, USER, 90);
        assertThat(acquire(b, USER)).isPositive();
    }

    @Test
    @DisplayName("새 인스턴스는 ZSET 을 지우지 않는다")
    void newInstance_doesNotClearZsets() {
        assertGranted(limiter(), USER, 450);

        assertThat(acquire(limiter(), USER)).isPositive();
    }

    @Test
    @DisplayName("배운 app 한도가 기본값을 덮어쓴다")
    void learnedAppLimit_overridesDefault() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.learnLimits(ROUTING, METHOD, "100:10", null);

        assertGranted(limiter, USER, 90);
        assertThat(acquire(limiter, USER)).isPositive();
    }

    @Test
    @DisplayName("method 한도는 그 method 에만 적용된다")
    void methodLimit_appliesPerMethod() {
        RedisRiotRateLimiter limiter = limiter();
        // 200:10 → method 상한 180
        limiter.learnLimits(ROUTING, METHOD, null, "200:10");

        assertGranted(limiter, USER, 180);
        assertThat(acquire(limiter, USER)).isPositive();
        assertThat(limiter.acquire(nextId(), ROUTING, "other", USER)).isZero();
    }

    @Test
    @DisplayName("빈 헤더·잘못된 헤더는 무시하고 이전 한도를 유지한다")
    void learnLimits_ignoresBlankHeader() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.learnLimits(ROUTING, METHOD, "100:10", null);
        limiter.learnLimits(ROUTING, METHOD, "", "");
        limiter.learnLimits(ROUTING, METHOD, null, null);
        limiter.learnLimits(ROUTING, METHOD, "500", "500:0");

        assertGranted(limiter, USER, 90);
        assertThat(acquire(limiter, USER)).isPositive();
    }

    @Test
    @DisplayName("같은 한도는 30초 안에는 다시 쓰지 않고, 30초가 지나면 다시 써서 잃어버린 한도를 복구한다")
    void learnLimits_rewritesUnchangedLimitAfterRefreshInterval() {
        RedisRiotRateLimiter limiter = limiter();
        limiter.learnLimits(ROUTING, METHOD, "100:10", null);
        limitsHash().delete();

        clock.addAndGet(29_999);
        limiter.learnLimits(ROUTING, METHOD, "100:10", null);
        assertThat(limitsHash().isExists()).isFalse();

        clock.addAndGet(2);
        limiter.learnLimits(ROUTING, METHOD, "100:10", null);
        assertThat(limitsHash().get("asia|app")).isEqualTo("100:10");
    }

    @Test
    @DisplayName("Redis 스크립트 캐시가 비워지면 다시 올리고 호출한다")
    void scriptCacheFlushed_reloadsScript() {
        RedisRiotRateLimiter limiter = limiter();
        assertThat(limiter.acquire("e1", ROUTING, METHOD, USER)).isZero();
        limiter.complete("e1", ROUTING, METHOD, USER);

        redisson.getScript().scriptFlush();

        assertThat(limiter.acquire("e2", ROUTING, METHOD, USER)).isZero();
        assertThat(zset(APP_10).size()).isEqualTo(2);
        clock.addAndGet(300);
        limiter.complete("e1", ROUTING, METHOD, USER);
        assertThat(zset(APP_10).getScore("e1")).isEqualTo((double) (NOW + 300));

        limiter.pause(ROUTING, null, Duration.ofSeconds(1));
        redisson.getScript().scriptFlush();
        limiter.pause(ROUTING, null, Duration.ofSeconds(2));
        assertThat(acquire(limiter, USER)).isGreaterThanOrEqualTo(2000);
    }

    @Test
    @DisplayName("시계를 주지 않으면 Redis TIME 으로 센다")
    void redisClock_whenNoOverride() {
        RedisRiotRateLimiter limiter = new RedisRiotRateLimiter(redisson, new RiotAPIProperties());
        assertThat(limiter.acquire("e1", ROUTING, METHOD, USER)).isZero();

        limiter.pause(ROUTING, null, Duration.ofSeconds(2));

        assertThat(limiter.acquire("e2", ROUTING, METHOD, USER)).isBetween(1500L, 2000L);
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

    /** 테스트 안에서 겹치지 않는 eventId. */
    private String nextId() {
        return "e" + seq++;
    }

    private long acquire(RiotRateLimiter limiter, RiotCallPriority priority) {
        return limiter.acquire(nextId(), ROUTING, METHOD, priority);
    }

    private void assertGranted(RiotRateLimiter limiter, RiotCallPriority priority, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(acquire(limiter, priority)).as("%s %d번째", priority, i + 1).isZero();
        }
    }

    private static RScoredSortedSet<String> zset(String key) {
        return redisson.getScoredSortedSet(key, StringCodec.INSTANCE);
    }

    private static RMap<String, String> limitsHash() {
        return redisson.getMap("riot:rl:limits", StringCodec.INSTANCE);
    }
}
