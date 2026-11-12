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

/** 한도 지표: Redis 의 규칙 ZSET·정지 키를 읽어 routing·버킷·창별 사용 수·상한, 정지 남은 시간을 낸다. */
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
