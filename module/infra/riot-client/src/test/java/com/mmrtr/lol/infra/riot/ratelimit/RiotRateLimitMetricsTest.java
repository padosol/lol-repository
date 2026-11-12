package com.mmrtr.lol.infra.riot.ratelimit;

import static com.mmrtr.lol.support.riot.RiotCallPriority.BACKGROUND;
import static com.mmrtr.lol.support.riot.RiotCallPriority.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.support.riot.RiotMethod;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
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

/**
 * 한도 지표: Redis 의 규칙 ZSET·정지 키·Riot 헤더 기록을 읽어 routing·버킷·창별 사용 수·상한·설정 한도, 정지 남은 시간,
 * Riot 이 알려 준 한도·집계 수를 낸다.
 */
@Testcontainers
class RiotRateLimitMetricsTest {

    private static final long NOW = 1_800_000_000_500L;

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static RedissonClient redisson;

    private final AtomicLong clock = new AtomicLong(NOW);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private RedisRiotRateLimiter limiter;
    private RiotRateLimitMetrics metrics;

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
        RiotAPIProperties properties = new RiotAPIProperties();
        properties.getRateLimit().setAppLimits("500:10,30000:600");
        properties.getRateLimit().setMethodLimits(Map.of("match-v5.match", "2000:10"));
        limiter = new RedisRiotRateLimiter(redisson, properties, clock::get, registry);
        metrics = new RiotRateLimitMetrics(limiter, redisson, registry, clock::get);
    }

    @Test
    @DisplayName("허가된 호출 수와 상한을 routing·버킷·창별로 낸다")
    void reportsUsedAndCapPerRule() {
        limiter.tryAcquire("KR", USER, Map.of(RiotMethod.MATCH, 3));

        metrics.refresh();

        assertThat(used("asia", "app", "10")).isEqualTo(3);
        assertThat(cap("asia", "app", "10")).isEqualTo(450);
        assertThat(used("asia", "app", "600")).isEqualTo(3);
        assertThat(cap("asia", "app", "600")).isEqualTo(27000);
        assertThat(used("asia", "match-v5.match", "10")).isEqualTo(3);
        assertThat(cap("asia", "match-v5.match", "10")).isEqualTo(1800);
    }

    @Test
    @DisplayName("창을 지난 기록은 세지 않는다")
    void expiredRecordsNotCounted() {
        limiter.tryAcquire("KR", USER, Map.of(RiotMethod.MATCH, 1));
        clock.addAndGet(10_000);

        metrics.refresh();

        assertThat(used("asia", "app", "10")).isZero();
        assertThat(used("asia", "app", "600")).isEqualTo(1);
    }

    @Test
    @DisplayName("기록이 없는 routing·버킷은 내지 않는다")
    void idleRulesNotReported() {
        limiter.tryAcquire("KR", USER, Map.of(RiotMethod.MATCH, 1));

        metrics.refresh();

        assertThat(gauge("riot.ratelimit.used", "europe", "app", "10")).isNull();
        assertThat(gauge("riot.ratelimit.used", "kr", "app", "10")).isNull();
        assertThat(gauge("riot.ratelimit.cap", "asia", "match-v5.timeline", "10")).isNull();
    }

    @Test
    @DisplayName("BACKGROUND 허가는 백필 규칙(1초, 상한 40)으로도 센다")
    void backgroundRuleReported() {
        limiter.tryAcquire("KR", BACKGROUND, Map.of(RiotMethod.MATCH, 2));

        metrics.refresh();

        assertThat(used("asia", "background", "1")).isEqualTo(2);
        assertThat(cap("asia", "background", "1")).isEqualTo(40);
    }

    @Test
    @DisplayName("정지 중인 버킷의 남은 시간(초)을 내고, 정지 키가 없어지면 지운다")
    void pausedBucketsReportedUntilKeyGone() {
        limiter.pause("asia", null, Duration.ofSeconds(5));
        limiter.pause("kr", "summoner-v4.by-puuid", Duration.ofSeconds(2));

        metrics.refresh();

        assertThat(pauseRemaining("asia", "app")).isBetween(0.001, 5.0);
        assertThat(pauseRemaining("kr", "summoner-v4.by-puuid")).isBetween(0.001, 2.0);

        redisson.getBucket("riot:rl:pause:asia|app", StringCodec.INSTANCE).delete();
        metrics.refresh();

        assertThat(registry.find("riot.ratelimit.pause.remaining").tags("routing", "asia", "bucket", "app").gauge())
                .isNull();
        assertThat(pauseRemaining("kr", "summoner-v4.by-puuid")).isPositive();
    }

    @Test
    @DisplayName("Riot 이 알려 준 한도·집계 수를 routing·버킷·창별로 내고, 설정 한도도 함께 낸다")
    void reportsRiotLimitsAndCounts_besideConfiguredLimit() {
        limiter.tryAcquire("KR", USER, Map.of(RiotMethod.MATCH, 1));
        limiter.observe("asia", "match-v5.match",
                new RiotLimitHeaders("20:1,100:120", "1:1,7:120", "2000:10", "1:10"));

        metrics.refresh();

        assertThat(observedLimit("asia", "app", "1")).isEqualTo(20);
        assertThat(observedLimit("asia", "app", "120")).isEqualTo(100);
        assertThat(observedCount("asia", "app", "1")).isEqualTo(1);
        assertThat(observedCount("asia", "app", "120")).isEqualTo(7);
        assertThat(observedLimit("asia", "match-v5.match", "10")).isEqualTo(2000);
        assertThat(observedCount("asia", "match-v5.match", "10")).isEqualTo(1);
        assertThat(limit("asia", "app", "10")).isEqualTo(500);
        assertThat(limit("asia", "app", "600")).isEqualTo(30000);
        assertThat(limit("asia", "match-v5.match", "10")).isEqualTo(2000);
    }

    @Test
    @DisplayName("Riot 집계 수는 받은 뒤 그 창이 지나면 내지 않고, 한도는 계속 낸다")
    void staleRiotCountDropped_limitKept() {
        limiter.observe("asia", "match-v5.match",
                new RiotLimitHeaders("500:10,30000:600", "3:10,40:600", null, null));
        clock.addAndGet(10_000);

        metrics.refresh();

        assertThat(gauge("riot.ratelimit.observed.count", "asia", "app", "10")).isNull();
        assertThat(observedCount("asia", "app", "600")).isEqualTo(40);
        assertThat(observedLimit("asia", "app", "10")).isEqualTo(500);
        assertThat(gauge("riot.ratelimit.observed.limit", "asia", "match-v5.match", "10")).isNull();
    }

    @Test
    @DisplayName("나중 응답의 헤더가 앞 값을 덮어쓰고, 한도 설정이 없는 method 도 Riot 값은 낸다")
    void laterObservationOverwrites_unconfiguredMethodReported() {
        limiter.observe("kr", "league-v4.entries", new RiotLimitHeaders("500:10", "1:10", "50:10", "1:10"));
        limiter.observe("kr", "league-v4.entries", new RiotLimitHeaders("500:10", "2:10", "50:10", "2:10"));

        metrics.refresh();

        assertThat(observedCount("kr", "app", "10")).isEqualTo(2);
        assertThat(observedLimit("kr", "league-v4.entries", "10")).isEqualTo(50);
        assertThat(observedCount("kr", "league-v4.entries", "10")).isEqualTo(2);
        assertThat(gauge("riot.ratelimit.limit", "kr", "league-v4.entries", "10")).isNull();
    }

    @Test
    @DisplayName("시계를 주지 않으면 기록·읽기 모두 Redis TIME 을 써서 방금 받은 집계 수를 낸다")
    void redisClock_freshCountReported() {
        RiotAPIProperties properties = new RiotAPIProperties();
        RedisRiotRateLimiter redisClockLimiter = new RedisRiotRateLimiter(redisson, properties, null, registry);
        RiotRateLimitMetrics redisClockMetrics = new RiotRateLimitMetrics(redisClockLimiter, redisson, registry, null);
        redisClockLimiter.observe("asia", "match-v5.match", new RiotLimitHeaders("500:10", "4:10", null, null));

        redisClockMetrics.refresh();

        assertThat(observedCount("asia", "app", "10")).isEqualTo(4);
    }

    @Test
    @DisplayName("Riot 값은 마지막으로 받은 뒤 하루가 지나면 Redis 에서 사라진다")
    void observedKeysExpireAfterADay() {
        limiter.observe("asia", "match-v5.match", new RiotLimitHeaders("500:10", "1:10", "2000:10", "1:10"));

        assertThat(redisson.getKeys().remainTimeToLive("riot:rl:observed:asia|app"))
                .isBetween(86_000_000L, 86_400_000L);
        assertThat(redisson.getKeys().remainTimeToLive("riot:rl:observed:asia|m|match-v5.match"))
                .isBetween(86_000_000L, 86_400_000L);
    }

    @Test
    @DisplayName("Redis 를 읽지 못해도 예외를 던지지 않는다")
    void redisFailure_doesNotThrow() {
        RedissonClient broken = mock(RedissonClient.class);
        given(broken.getScript(StringCodec.INSTANCE)).willThrow(new IllegalStateException("redis down"));
        RiotRateLimitMetrics failing = new RiotRateLimitMetrics(limiter, broken, new SimpleMeterRegistry(), clock::get);

        assertThatCode(failing::refresh).doesNotThrowAnyException();
    }

    private double used(String routing, String bucket, String window) {
        return value("riot.ratelimit.used", routing, bucket, window);
    }

    private double cap(String routing, String bucket, String window) {
        return value("riot.ratelimit.cap", routing, bucket, window);
    }

    private double limit(String routing, String bucket, String window) {
        return value("riot.ratelimit.limit", routing, bucket, window);
    }

    private double observedLimit(String routing, String bucket, String window) {
        return value("riot.ratelimit.observed.limit", routing, bucket, window);
    }

    private double observedCount(String routing, String bucket, String window) {
        return value("riot.ratelimit.observed.count", routing, bucket, window);
    }

    private double value(String name, String routing, String bucket, String window) {
        Gauge gauge = gauge(name, routing, bucket, window);
        assertThat(gauge).as("%s{routing=%s,bucket=%s,window=%s}", name, routing, bucket, window).isNotNull();
        return gauge.value();
    }

    private Gauge gauge(String name, String routing, String bucket, String window) {
        return registry.find(name).tags("routing", routing, "bucket", bucket, "window", window).gauge();
    }

    private double pauseRemaining(String routing, String bucket) {
        Gauge gauge = registry.find("riot.ratelimit.pause.remaining").tags("routing", routing, "bucket", bucket).gauge();
        assertThat(gauge).as("정지 %s|%s", routing, bucket).isNotNull();
        return gauge.value();
    }
}
