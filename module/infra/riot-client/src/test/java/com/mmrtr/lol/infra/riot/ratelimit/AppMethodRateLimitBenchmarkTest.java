package com.mmrtr.lol.infra.riot.ratelimit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여러 앱 인스턴스(RedissonClient 4개 × 16 스레드)가 한 Redis 의 app/method 버킷을 동시에 두드릴 때
 * 전략별 허용 수·한도 초과(샘)·app 슬롯 낭비·지연을 측정해 build/reports/ratelimit/ 에 markdown 으로 남긴다.
 * 모든 요청이 한 윈도우(10s) 안에 끝나는 버스트라 "윈도우 1개 안에서의 정확도"만 본다.
 */
@Testcontainers(disabledWithoutDocker = true)
class AppMethodRateLimitBenchmarkTest {

    private static final int INSTANCES = 4;
    private static final int THREADS_PER_INSTANCE = 16;
    private static final int POOL_SIZE_PER_INSTANCE = 10;
    private static final int TRIALS = 20;
    private static final Duration WINDOW = Duration.ofSeconds(10);
    private static final Path REPORT = Path.of("build/reports/ratelimit/app-method-ratelimit-benchmark.md");

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("S1 단일 메서드 (method 한도 < app 한도)",
                    100, limits("A", 50), demand("A", 1000), 50),
            new Scenario("S2 app 공유 · 수요 넉넉 (A 가 먼저 막히는 method)",
                    100, limits("A", 30, "B", 1000), demand("A", 500, "B", 500), 100),
            new Scenario("S3 app 공유 · 수요 = 이상적 허용량 (여유 0)",
                    100, limits("A", 30, "B", 1000), demand("A", 60, "B", 70), 100));

    @Container
    private static final GenericContainer<?> REDIS = RedisTestSupport.newContainer();

    private static List<RedissonClient> instances;
    private static ExecutorService workers;

    @BeforeAll
    static void setUp() {
        instances = new ArrayList<>(INSTANCES);
        for (int i = 0; i < INSTANCES; i++) {
            instances.add(RedisTestSupport.connect(REDIS, POOL_SIZE_PER_INSTANCE));
        }
        workers = Executors.newFixedThreadPool(INSTANCES * THREADS_PER_INSTANCE);
    }

    @AfterAll
    static void tearDown() {
        workers.shutdownNow();
        instances.forEach(RedissonClient::shutdown);
    }

    @Test
    @DisplayName("동시 버스트에서 전략별 한도 정확도·초과 허용·app 슬롯 낭비를 측정한다 (Lua 는 항상 정확해야 한다)")
    void compareStrategies_concurrentBurst_reportsAccuracyAndLeaks() throws Exception {
        // given
        List<String> violations = new ArrayList<>();
        StringBuilder report = new StringBuilder(reportHeader());
        for (Scenario scenario : SCENARIOS) {
            for (RateLimitStrategy strategy : RateLimitStrategy.values()) {
                runTrial(strategy, scenario, -1);
            }

            // when
            Map<RateLimitStrategy, List<Trial>> results = new EnumMap<>(RateLimitStrategy.class);
            for (int t = 0; t < TRIALS; t++) {
                for (RateLimitStrategy strategy : RateLimitStrategy.values()) {
                    results.computeIfAbsent(strategy, k -> new ArrayList<>()).add(runTrial(strategy, scenario, t));
                }
            }

            report.append(scenarioTable(scenario, results));
            violations.addAll(checkInvariants(scenario, results));
        }
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report);
        System.out.println(report);

        // then
        assertThat(violations).isEmpty();
    }

    private Trial runTrial(RateLimitStrategy strategy, Scenario scenario, long seed) throws Exception {
        String prefix = "{rl:" + UUID.randomUUID() + "}";
        RateBucket app = new RateBucket(prefix + ":app", scenario.appLimit(), WINDOW);
        Map<String, RateBucket> methods = new LinkedHashMap<>();
        scenario.methodLimits().forEach((name, limit) ->
                methods.put(name, new RateBucket(prefix + ":" + name, limit, WINDOW)));
        List<String> attempts = shuffledAttempts(scenario.demand(), seed);

        List<MultiBucketRateLimiter> limiters = instances.stream().map(strategy::create).toList();
        List<RateBucket> allBuckets = new ArrayList<>(methods.values());
        allBuckets.add(0, app);
        limiters.get(0).prepare(allBuckets);

        Map<String, LongAdder> admittedByMethod = new LinkedHashMap<>();
        methods.keySet().forEach(name -> admittedByMethod.put(name, new LongAdder()));
        long[] latencies = new long[attempts.size()];
        AtomicInteger cursor = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> running = new ArrayList<>();
        for (int w = 0; w < INSTANCES * THREADS_PER_INSTANCE; w++) {
            MultiBucketRateLimiter limiter = limiters.get(w % INSTANCES);
            running.add(workers.submit(() -> {
                start.await();
                for (int i = cursor.getAndIncrement(); i < attempts.size(); i = cursor.getAndIncrement()) {
                    String method = attempts.get(i);
                    long begin = System.nanoTime();
                    boolean admitted = limiter.tryAcquire(List.of(app, methods.get(method)));
                    latencies[i] = System.nanoTime() - begin;
                    if (admitted) {
                        admittedByMethod.get(method).increment();
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : running) {
            future.get(60, TimeUnit.SECONDS);
        }

        Map<String, Long> admitted = new LinkedHashMap<>();
        admittedByMethod.forEach((name, count) -> admitted.put(name, count.sum()));
        long total = admitted.values().stream().mapToLong(Long::longValue).sum();
        long overshoot = Math.max(0, total - app.limit());
        for (Map.Entry<String, RateBucket> method : methods.entrySet()) {
            overshoot = Math.max(overshoot, admitted.get(method.getKey()) - method.getValue().limit());
        }
        long appWasted = Math.max(0, limiters.get(0).consumed(app) - total);
        return new Trial(total, admitted, overshoot, appWasted, latencies);
    }

    private static List<String> checkInvariants(Scenario scenario, Map<RateLimitStrategy, List<Trial>> results) {
        List<String> violations = new ArrayList<>();
        for (Trial trial : results.get(RateLimitStrategy.LUA_ATOMIC)) {
            if (trial.admitted() != scenario.ideal() || trial.overshoot() != 0 || trial.appWasted() != 0) {
                violations.add(scenario.name() + " / LUA_ATOMIC: " + trial);
            }
        }
        for (RateLimitStrategy atomicPerKey : List.of(RateLimitStrategy.SEQUENTIAL_INCR,
                RateLimitStrategy.SEQUENTIAL_INCR_ROLLBACK, RateLimitStrategy.SEQUENTIAL_REDISSON)) {
            for (Trial trial : results.get(atomicPerKey)) {
                if (trial.overshoot() != 0) {
                    violations.add(scenario.name() + " / " + atomicPerKey + " overshoot: " + trial);
                }
            }
        }
        return violations;
    }

    private static String reportHeader() {
        return """
                # app → method rate limit: 순차 vs Lua 벤치마크 결과

                - Redis: `redis:7.4-alpine` (Testcontainers, 단일 노드)
                - 클라이언트: RedissonClient %d개(앱 인스턴스 가정, 풀 %d) × 인스턴스당 %d 스레드 = 동시 %d
                - 시나리오×전략마다 %d trial (워밍업 1회 제외), trial 마다 새 키 · 요청 순서는 seed 로 셔플
                - 윈도우 %ds — 모든 요청이 한 윈도우 안에서 끝나는 버스트
                - JVM: %s / OS: %s %s

                용어
                - **허용**: tryAcquire 가 true 를 돌려준 요청 수 (= 실제로 Riot 에 나갔을 요청 수)
                - **이상치**: 한도를 정확히 지키면서 낭비 없이 허용할 수 있는 최대 요청 수
                - **초과(샘)**: 어떤 버킷이든 허용 수가 한도를 넘은 양. 0 이 아니면 Riot 이 429 를 돌려줄 요청이다
                - **app 낭비**: app 버킷에서 쓰였지만 실제 허용으로 이어지지 않은 슬롯 (윈도우가 끝날 때까지 아무도 못 씀)

                """.formatted(INSTANCES, POOL_SIZE_PER_INSTANCE, THREADS_PER_INSTANCE,
                INSTANCES * THREADS_PER_INSTANCE, TRIALS, WINDOW.toSeconds(),
                System.getProperty("java.version"), System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    private static String scenarioTable(Scenario scenario, Map<RateLimitStrategy, List<Trial>> results) {
        StringBuilder table = new StringBuilder();
        table.append("## ").append(scenario.name()).append("\n\n")
                .append("app 한도 ").append(scenario.appLimit())
                .append(" · method 한도 ").append(scenario.methodLimits())
                .append(" · 요청 ").append(scenario.demand())
                .append(" · **이상치 ").append(scenario.ideal()).append("**\n\n")
                .append("| 전략 | 허용 min / avg / max | 이상치 대비 avg | 초과(샘) max · 발생 trial | app 낭비 avg (max)"
                        + " | method 별 허용 avg | 지연 p50 / p99 (µs) | Redis 왕복/요청 |\n")
                .append("|---|---|---|---|---|---|---|---|\n");
        for (Map.Entry<RateLimitStrategy, List<Trial>> entry : results.entrySet()) {
            RateLimitStrategy strategy = entry.getKey();
            List<Trial> trials = entry.getValue();
            long[] latencies = trials.stream().flatMapToLong(t -> Arrays.stream(t.latenciesNanos())).sorted().toArray();
            long overshootTrials = trials.stream().filter(t -> t.overshoot() > 0).count();
            table.append(String.format("| %s | %d / %.1f / %d | %+.1f | %d · %d/%d | %.1f (%d) | %s | %d / %d | %s |%n",
                    strategy.label(),
                    trials.stream().mapToLong(Trial::admitted).min().orElse(0),
                    trials.stream().mapToLong(Trial::admitted).average().orElse(0),
                    trials.stream().mapToLong(Trial::admitted).max().orElse(0),
                    trials.stream().mapToLong(Trial::admitted).average().orElse(0) - scenario.ideal(),
                    trials.stream().mapToLong(Trial::overshoot).max().orElse(0), overshootTrials, trials.size(),
                    trials.stream().mapToLong(Trial::appWasted).average().orElse(0),
                    trials.stream().mapToLong(Trial::appWasted).max().orElse(0),
                    perMethodAverage(scenario, trials),
                    percentileMicros(latencies, 0.50), percentileMicros(latencies, 0.99),
                    strategy.roundTrips()));
        }
        return table.append('\n').toString();
    }

    private static String perMethodAverage(Scenario scenario, List<Trial> trials) {
        List<String> parts = new ArrayList<>();
        for (String method : scenario.methodLimits().keySet()) {
            double avg = trials.stream().mapToLong(t -> t.admittedByMethod().get(method)).average().orElse(0);
            parts.add(String.format("%s %.1f", method, avg));
        }
        return String.join(" / ", parts);
    }

    private static long percentileMicros(long[] sortedNanos, double percentile) {
        if (sortedNanos.length == 0) {
            return 0;
        }
        int index = (int) Math.min(sortedNanos.length - 1, Math.ceil(percentile * sortedNanos.length) - 1);
        return TimeUnit.NANOSECONDS.toMicros(sortedNanos[Math.max(0, index)]);
    }

    private static List<String> shuffledAttempts(Map<String, Integer> demand, long seed) {
        List<String> attempts = new ArrayList<>();
        demand.forEach((method, count) -> attempts.addAll(Collections.nCopies(count, method)));
        Collections.shuffle(attempts, new Random(seed));
        return attempts;
    }

    private static Map<String, Long> limits(Object... nameAndLimit) {
        Map<String, Long> limits = new LinkedHashMap<>();
        for (int i = 0; i < nameAndLimit.length; i += 2) {
            limits.put((String) nameAndLimit[i], ((Number) nameAndLimit[i + 1]).longValue());
        }
        return limits;
    }

    private static Map<String, Integer> demand(Object... nameAndCount) {
        Map<String, Integer> demand = new LinkedHashMap<>();
        for (int i = 0; i < nameAndCount.length; i += 2) {
            demand.put((String) nameAndCount[i], (Integer) nameAndCount[i + 1]);
        }
        return demand;
    }

    private record Scenario(String name, long appLimit, Map<String, Long> methodLimits,
            Map<String, Integer> demand, long ideal) {
    }

    private record Trial(long admitted, Map<String, Long> admittedByMethod, long overshoot, long appWasted,
            long[] latenciesNanos) {

        @Override
        public String toString() {
            return "Trial[admitted=" + admitted + ", byMethod=" + admittedByMethod
                    + ", overshoot=" + overshoot + ", appWasted=" + appWasted + "]";
        }
    }
}
