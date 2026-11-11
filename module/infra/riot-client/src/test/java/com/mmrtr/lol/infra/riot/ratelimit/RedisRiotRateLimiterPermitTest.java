package com.mmrtr.lol.infra.riot.ratelimit;

import static com.mmrtr.lol.support.riot.RiotCallPriority.BACKGROUND;
import static com.mmrtr.lol.support.riot.RiotCallPriority.DEFERRED;
import static com.mmrtr.lol.support.riot.RiotCallPriority.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermits;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
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

/** 기본 app-limits "500:10" 이면 app 규칙 상한은 450, DEFERRED 가 유저 활동 중이면 360 이다. */
@Testcontainers
class RedisRiotRateLimiterPermitTest {

    private static final long NOW = 1_800_000_000_500L;
    private static final String ASIA_APP = RedisRiotRateLimiter.ruleKey("asia|app", 10);
    private static final String KR_APP = RedisRiotRateLimiter.ruleKey("kr|app", 10);
    private static final String ASIA_BG = RedisRiotRateLimiter.ruleKey("asia|bg", 1);
    private static final String ASIA_MATCH = RedisRiotRateLimiter.ruleKey("asia|m|match-v5.match", 10);
    private static final String ASIA_TIMELINE = RedisRiotRateLimiter.ruleKey("asia|m|match-v5.timeline", 10);

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static RedissonClient redisson;

    private final AtomicLong clock = new AtomicLong(NOW);
    private RedisRiotRateLimiter limiter;

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
        limiter = new RedisRiotRateLimiter(redisson, new RiotAPIProperties(), clock::get);
    }

    @Test
    @DisplayName("묶음을 허가하고 app·method 규칙마다 토큰을 기록한다")
    void grantsBatchAndRecordsTokensPerRule() {
        limiter = withMethodLimits(Map.of("match-v5.match", "2000:10", "match-v5.timeline", "2000:10"));

        Optional<RiotPermits> granted = limiter.tryAcquire("KR", USER,
                Map.of(RiotMethod.MATCH, 3, RiotMethod.MATCH_TIMELINE, 2));

        assertThat(granted).isPresent();
        assertThat(granted.get().remaining(RiotMethod.MATCH)).isEqualTo(3);
        assertThat(granted.get().remaining(RiotMethod.MATCH_TIMELINE)).isEqualTo(2);
        assertThat(zset(ASIA_APP).size()).isEqualTo(5);
        assertThat(zset(ASIA_MATCH).size()).isEqualTo(3);
        assertThat(zset(ASIA_MATCH).readAll()).allMatch(t -> t.contains(":match-v5.match:"));
        assertThat(zset(ASIA_TIMELINE).size()).isEqualTo(2);
    }

    @Test
    @DisplayName("한 규칙이라도 넘치면 거절하고 아무것도 기록하지 않는다")
    void deniesWhenAnyRuleWouldOverflow_andRecordsNothing() {
        limiter = withMethodLimits(Map.of("match-v5.match", "2000:10"));
        fill(ASIA_APP, 448);

        Optional<RiotPermits> granted = limiter.tryAcquire("KR", USER, Map.of(RiotMethod.MATCH, 3));

        assertThat(granted).isEmpty();
        assertThat(zset(ASIA_APP).size()).isEqualTo(448);
        assertThat(zset(ASIA_MATCH).size()).isZero();
    }

    @Test
    @DisplayName("정지 중이면 거절한다")
    void deniesWhilePaused() {
        limiter.pause("asia", null, Duration.ofSeconds(5));

        assertThat(limiter.tryAcquire("KR", USER, Map.of(RiotMethod.MATCH, 1))).isEmpty();
        assertThat(zset(ASIA_APP).size()).isZero();

        clock.addAndGet(5_001);
        assertThat(limiter.tryAcquire("KR", USER, Map.of(RiotMethod.MATCH, 1))).isPresent();
    }

    @Test
    @DisplayName("유저 활동 중이면 DEFERRED 상한이 floor(450 × 0.8) = 360 으로 낮아진다")
    void userActiveLowersDeferredCap() {
        assertThat(limiter.tryAcquire("KR", USER, Map.of(RiotMethod.MATCH, 1))).isPresent();

        assertThat(limiter.tryAcquire("KR", DEFERRED, Map.of(RiotMethod.MATCH, 359))).isPresent();
        assertThat(limiter.tryAcquire("KR", DEFERRED, Map.of(RiotMethod.MATCH, 1))).isEmpty();
        assertThat(zset(ASIA_APP).size()).isEqualTo(360);
    }

    @Test
    @DisplayName("백필 규칙(초당 40)은 BACKGROUND 에만 적용된다")
    void backgroundRuleAppliesOnlyToBackground() {
        assertThat(limiter.tryAcquire("KR", BACKGROUND, Map.of(RiotMethod.MATCH, 41))).isEmpty();
        assertThat(limiter.tryAcquire("KR", USER, Map.of(RiotMethod.MATCH, 41))).isPresent();
        assertThat(zset(ASIA_BG).size()).isZero();
    }

    @Test
    @DisplayName("상한(18)보다 큰 요청은 언제나 거절한다")
    void needLargerThanCapAlwaysDenied() {
        RiotAPIProperties properties = new RiotAPIProperties();
        properties.getRateLimit().setAppLimits("20:1");
        RedisRiotRateLimiter small = new RedisRiotRateLimiter(redisson, properties, clock::get);

        assertThat(small.tryAcquire("KR", USER, Map.of(RiotMethod.MATCH, 19))).isEmpty();
        clock.addAndGet(10_000);
        assertThat(small.tryAcquire("KR", USER, Map.of(RiotMethod.MATCH, 19))).isEmpty();
        assertThat(small.tryAcquire("KR", USER, Map.of(RiotMethod.MATCH, 18))).isPresent();
    }

    @Test
    @DisplayName("거절돼도 USER 요청은 유저 활동 표시를 켠다")
    void userRequestSetsActivityEvenWhenDenied() {
        fill(ASIA_APP, 450);

        assertThat(limiter.tryAcquire("KR", USER, Map.of(RiotMethod.MATCH, 1))).isEmpty();

        assertThat(redisson.getBucket("riot:rl:user:asia", StringCodec.INSTANCE).isExists()).isTrue();
    }

    @Test
    @DisplayName("regional 은 허가되고 platform 이 거절되면 빈 값이고, regional 토큰은 남긴다")
    void platformDeniedAfterRegionalGranted_returnsEmptyAndKeepsRegionalTokens() {
        fill(KR_APP, 450);

        Optional<RiotPermits> granted = limiter.tryAcquire("KR", USER, Map.of(
                RiotMethod.ACCOUNT_BY_RIOT_ID, 1, RiotMethod.SUMMONER_BY_PUUID, 1,
                RiotMethod.LEAGUE_ENTRIES_BY_PUUID, 1));

        assertThat(granted).isEmpty();
        assertThat(zset(ASIA_APP).size()).isEqualTo(1);
        assertThat(zset(KR_APP).size()).isEqualTo(450);
    }

    @Test
    @DisplayName("빈 요청이나 1 미만 건수는 IllegalArgumentException")
    void rejectsEmptyOrNonPositiveCounts() {
        assertThatThrownBy(() -> limiter.tryAcquire("KR", USER, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.tryAcquire("KR", USER, Map.of(RiotMethod.MATCH, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("routing 은 호스트의 첫 점 앞이다")
    void routingOfUsesHostPrefix() {
        assertThat(RedisRiotRateLimiter.routingOf(com.mmrtr.lol.common.type.Platform.KR,
                RiotMethod.Routing.REGIONAL)).isEqualTo("asia");
        assertThat(RedisRiotRateLimiter.routingOf(com.mmrtr.lol.common.type.Platform.EUW,
                RiotMethod.Routing.PLATFORM)).isEqualTo("euw1");
    }

    private RedisRiotRateLimiter withMethodLimits(Map<String, String> methodLimits) {
        RiotAPIProperties properties = new RiotAPIProperties();
        properties.getRateLimit().setMethodLimits(methodLimits);
        return new RedisRiotRateLimiter(redisson, properties, clock::get);
    }

    private void fill(String key, int count) {
        for (int i = 0; i < count; i++) {
            zset(key).add(NOW, "pre-" + i);
        }
    }

    private static RScoredSortedSet<String> zset(String key) {
        return redisson.getScoredSortedSet(key, StringCodec.INSTANCE);
    }
}
