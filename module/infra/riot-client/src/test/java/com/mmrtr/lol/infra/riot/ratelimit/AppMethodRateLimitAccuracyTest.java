package com.mmrtr.lol.infra.riot.ratelimit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * app → method 순차 검사와 Lua 단일 검사의 "한도 정확도 / 새는 슬롯"을 실제 Redis 로 검증한다.
 * 경합은 훅(배리어·래치)으로 순서를 고정해 결정적으로 재현한다. 수치 비교는 {@link AppMethodRateLimitBenchmarkTest}.
 */
@Testcontainers(disabledWithoutDocker = true)
class AppMethodRateLimitAccuracyTest {

    private static final Duration WINDOW = Duration.ofSeconds(10);

    @Container
    private static final GenericContainer<?> REDIS = RedisTestSupport.newContainer();

    private static RedissonClient redisson;

    private String keyPrefix;

    @BeforeAll
    static void connect() {
        redisson = RedisTestSupport.connect(REDIS, 32);
    }

    @AfterAll
    static void shutdown() {
        redisson.shutdown();
    }

    @BeforeEach
    void newKeySpace() {
        keyPrefix = "{rl:" + UUID.randomUUID() + "}";
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = RateLimitStrategy.class, names = {"SEQUENTIAL_INCR", "SEQUENTIAL_REDISSON"})
    @DisplayName("순차 검사: method 에서 거절돼도 app 슬롯은 이미 쓰여, 실제 요청 5건인데 app(10) 이 가득 차 다른 method 가 막힌다")
    void tryAcquire_methodRejectsAfterAppPassed_burnsAppSlot(RateLimitStrategy strategy) {
        // given
        RateBucket app = bucket("app", 10);
        RateBucket methodA = bucket("A", 5);
        RateBucket methodB = bucket("B", 100);
        MultiBucketRateLimiter limiter = strategy.create(redisson);
        limiter.prepare(List.of(app, methodA, methodB));

        // when
        int admittedA = acquireRepeatedly(limiter, List.of(app, methodA), 10);
        boolean admittedB = limiter.tryAcquire(List.of(app, methodB));

        // then
        assertThat(admittedA).isEqualTo(5);
        assertThat(limiter.consumed(app)).isEqualTo(10);
        assertThat(admittedB).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = RateLimitStrategy.class, names = {"LUA_ATOMIC", "SEQUENTIAL_INCR_ROLLBACK"})
    @DisplayName("Lua / 보상 DECR: 경합이 없으면 method 거절이 app 슬롯을 남기지 않아 다른 method 가 통과한다")
    void tryAcquire_methodRejects_leavesAppSlotUntouched(RateLimitStrategy strategy) {
        // given
        RateBucket app = bucket("app", 10);
        RateBucket methodA = bucket("A", 5);
        RateBucket methodB = bucket("B", 100);
        MultiBucketRateLimiter limiter = strategy.create(redisson);
        limiter.prepare(List.of(app, methodA, methodB));

        // when
        int admittedA = acquireRepeatedly(limiter, List.of(app, methodA), 10);
        boolean admittedB = limiter.tryAcquire(List.of(app, methodB));

        // then
        assertThat(admittedA).isEqualTo(5);
        assertThat(admittedB).isTrue();
        assertThat(limiter.consumed(app)).isEqualTo(6);
        assertThat(limiter.consumed(methodA)).isEqualTo(5);
    }

    @Test
    @DisplayName("비원자 GET→INCR: 8개 요청이 검사를 동시에 통과하면 한도 1 에 8건이 모두 허용된다 (7건 초과)")
    void naiveCheckThenIncr_checksInterleave_admitsBeyondLimit() throws Exception {
        // given
        int concurrency = 8;
        RateBucket app = bucket("app", 1);
        RateBucket methodA = bucket("A", 1);
        CyclicBarrier allChecked = new CyclicBarrier(concurrency);
        MultiBucketRateLimiter limiter = new NaiveCheckThenIncrLimiter(redisson, () -> awaitQuietly(allChecked));

        // when
        int admitted = runConcurrently(concurrency, () -> limiter.tryAcquire(List.of(app, methodA)));

        // then
        assertThat(admitted).isEqualTo(concurrency);
        assertThat(limiter.consumed(app)).isEqualTo(concurrency);
        assertThat(limiter.consumed(methodA)).isEqualTo(concurrency);
    }

    @Test
    @DisplayName("Lua: 같은 8개 동시 요청이어도 한도 1 이면 정확히 1건만 허용된다")
    void lua_concurrentRequestsAtLimitOne_admitsExactlyOne() throws Exception {
        // given
        RateBucket app = bucket("app", 1);
        RateBucket methodA = bucket("A", 1);
        MultiBucketRateLimiter limiter = new LuaAtomicLimiter(redisson);

        // when
        int admitted = runConcurrently(8, () -> limiter.tryAcquire(List.of(app, methodA)));

        // then
        assertThat(admitted).isEqualTo(1);
        assertThat(limiter.consumed(app)).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("Lua: 32 스레드가 app(100)·A(30)·B(1000) 에 400건을 쏘면 app 은 정확히 100, 카운터 = 실제 허용 수")
    void lua_heavyContention_fillsAppExactlyWithoutWaste() throws Exception {
        // given
        RateBucket app = bucket("app", 100);
        RateBucket methodA = bucket("A", 30);
        RateBucket methodB = bucket("B", 1000);
        MultiBucketRateLimiter limiter = new LuaAtomicLimiter(redisson);
        List<RateBucket> attempts = shuffledAttempts(methodA, 200, methodB, 200);
        AtomicInteger cursor = new AtomicInteger();
        AtomicInteger admittedA = new AtomicInteger();
        AtomicInteger admittedB = new AtomicInteger();

        // when
        runConcurrently(32, () -> {
            for (int i = cursor.getAndIncrement(); i < attempts.size(); i = cursor.getAndIncrement()) {
                RateBucket method = attempts.get(i);
                if (limiter.tryAcquire(List.of(app, method))) {
                    (method == methodA ? admittedA : admittedB).incrementAndGet();
                }
            }
            return false;
        });

        // then
        assertThat(admittedA.get() + admittedB.get()).isEqualTo(100);
        assertThat(admittedA.get()).isLessThanOrEqualTo(30);
        assertThat(limiter.consumed(app)).isEqualTo(100);
        assertThat(limiter.consumed(methodA)).isEqualTo(admittedA.get());
        assertThat(limiter.consumed(methodB)).isEqualTo(admittedB.get());
    }

    @Test
    @DisplayName("보상 DECR: 보상이 끝나기 전 끼어든 요청은 자리가 있는데도 거절된다 (최종 app 사용 1/2)")
    void rollback_compensationPending_rejectsRequestThatHadRoom() throws Exception {
        // given
        RateBucket app = bucket("app", 2);
        RateBucket methodA = bucket("A", 1);
        RateBucket methodB = bucket("B", 10);
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicBoolean firstRollback = new AtomicBoolean(true);
        MultiBucketRateLimiter limiter = new SequentialIncrRollbackLimiter(redisson, () -> {
            if (firstRollback.compareAndSet(true, false)) {
                paused.countDown();
                awaitQuietly(resume);
            }
        });
        assertThat(limiter.tryAcquire(List.of(app, methodA))).isTrue();
        CompletableFuture<Boolean> overflowA = CompletableFuture.supplyAsync(
                () -> limiter.tryAcquire(List.of(app, methodA)));
        assertThat(paused.await(5, TimeUnit.SECONDS)).isTrue();

        // when
        boolean admittedB = limiter.tryAcquire(List.of(app, methodB));
        resume.countDown();

        // then
        assertThat(overflowA.get(5, TimeUnit.SECONDS)).isFalse();
        assertThat(admittedB).isFalse();
        assertThat(limiter.consumed(app)).isEqualTo(1);
    }

    @Test
    @DisplayName("보상 DECR: 윈도우가 끝난 뒤 DECR 이 도착하면 만료 없는 -1 키가 생기고 다음 윈도우에 한도 3 을 넘어 4건이 허용된다")
    void rollback_decrementAfterWindowExpired_admitsBeyondLimitInNextWindow() {
        // given
        RateBucket app = new RateBucket(keyPrefix + ":app", 3, Duration.ofMillis(300));
        RateBucket methodA = bucket("A", 1);
        RateBucket methodB = bucket("B", 100);
        AtomicBoolean firstRollback = new AtomicBoolean(true);
        MultiBucketRateLimiter limiter = new SequentialIncrRollbackLimiter(redisson, () -> {
            if (firstRollback.compareAndSet(true, false)) {
                sleepQuietly(Duration.ofMillis(600));
            }
        });
        assertThat(limiter.tryAcquire(List.of(app, methodA))).isTrue();
        assertThat(limiter.tryAcquire(List.of(app, methodA))).isFalse();
        RAtomicLong appCounter = RedisCounters.counter(redisson, app);
        assertThat(appCounter.get()).isEqualTo(-1);
        assertThat(appCounter.remainTimeToLive()).isEqualTo(-1);

        // when
        int admittedInNextWindow = acquireRepeatedly(limiter, List.of(app, methodB), 10);

        // then
        assertThat(admittedInNextWindow).isEqualTo(4);
    }

    private RateBucket bucket(String name, long limit) {
        return new RateBucket(keyPrefix + ":" + name, limit, WINDOW);
    }

    private static int acquireRepeatedly(MultiBucketRateLimiter limiter, List<RateBucket> buckets, int times) {
        int admitted = 0;
        for (int i = 0; i < times; i++) {
            if (limiter.tryAcquire(buckets)) {
                admitted++;
            }
        }
        return admitted;
    }

    private static List<RateBucket> shuffledAttempts(RateBucket first, int firstCount,
            RateBucket second, int secondCount) {
        List<RateBucket> attempts = new ArrayList<>(firstCount + secondCount);
        attempts.addAll(Collections.nCopies(firstCount, first));
        attempts.addAll(Collections.nCopies(secondCount, second));
        Collections.shuffle(attempts, new Random(42));
        return attempts;
    }

    /** task 를 동시에 출발시켜 true 를 돌려준 개수를 센다. */
    private static int runConcurrently(int threads, Callable<Boolean> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            int admitted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    admitted++;
                }
            }
            return admitted;
        } finally {
            executor.shutdownNow();
        }
    }

    private static void awaitQuietly(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
