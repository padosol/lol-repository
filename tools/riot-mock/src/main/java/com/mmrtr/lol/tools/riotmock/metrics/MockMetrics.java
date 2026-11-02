package com.mmrtr.lol.tools.riotmock.metrics;

import com.mmrtr.lol.tools.riotmock.ratelimit.BucketUsage;
import com.mmrtr.lol.tools.riotmock.ratelimit.LimitSpec;
import com.mmrtr.lol.tools.riotmock.ratelimit.RateLimitEngine;
import com.mmrtr.lol.tools.riotmock.ratelimit.SlidingPeakTracker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 심판 지표. 카운터는 reset 해도 비우지 않는다 (판정은 구간 증가량으로 한다).
 * 게이지는 1초마다 엔진과 슬라이딩 피크 추적기의 현재 값으로 다시 그린다.
 */
@Component
public class MockMetrics {

    private static final String RATE_LIMITED = "riot_mock_rate_limited_total";
    private static final String UNKNOWN = "riot_mock_unknown_total";
    private static final List<String> RATE_LIMIT_TYPES = List.of(RateLimitEngine.APPLICATION, RateLimitEngine.METHOD);

    private final MeterRegistry registry;
    private final RateLimitEngine engine;
    private final SlidingPeakTracker tracker;
    private final Set<String> seenRoutings = ConcurrentHashMap.newKeySet();
    private final MultiGauge windowCount;
    private final MultiGauge windowLimit;
    private final MultiGauge slidingPeak;
    private final MultiGauge slidingPeakRatio;

    public MockMetrics(MeterRegistry registry, RateLimitEngine engine, SlidingPeakTracker tracker) {
        this.registry = registry;
        this.engine = engine;
        this.tracker = tracker;
        this.windowCount = MultiGauge.builder("riot_mock_window_count").register(registry);
        this.windowLimit = MultiGauge.builder("riot_mock_window_limit").register(registry);
        this.slidingPeak = MultiGauge.builder("riot_mock_sliding_peak").register(registry);
        this.slidingPeakRatio = MultiGauge.builder("riot_mock_sliding_peak_ratio").register(registry);
    }

    public void recordRequest(String routing, String endpoint, int status) {
        see(routing);
        registry.counter("riot_mock_requests_total",
                "routing", routing, "endpoint", endpoint, "status", String.valueOf(status)).increment();
    }

    public void recordRateLimited(String routing, String type) {
        see(routing);
        registry.counter(RATE_LIMITED, "routing", routing, "type", type).increment();
    }

    public void recordUnknown(String routing) {
        see(routing);
        registry.counter(UNKNOWN, "routing", routing).increment();
    }

    /**
     * routing 을 처음 보면 429·unknown 카운터를 0 으로 먼저 만든다.
     * 첫 429 때 시리즈가 생기면 Prometheus increase() 가 그 첫 값을 세지 않아 429 가 몰려도 0 으로 보인다.
     * 등록은 멱등이므로 먼저 등록하고 나서 본 것으로 표시한다 (표시를 본 스레드는 0 시리즈가 있음을 보장받는다).
     */
    private void see(String routing) {
        if (seenRoutings.contains(routing)) {
            return;
        }
        for (String type : RATE_LIMIT_TYPES) {
            registry.counter(RATE_LIMITED, "routing", routing, "type", type);
        }
        registry.counter(UNKNOWN, "routing", routing);
        seenRoutings.add(routing);
    }

    @Scheduled(fixedRate = 1000)
    public void refresh() {
        List<MultiGauge.Row<?>> counts = new ArrayList<>();
        List<MultiGauge.Row<?>> limits = new ArrayList<>();
        for (BucketUsage usage : engine.usage()) {
            Tags tags = Tags.of("bucket", usage.bucket(), "window", String.valueOf(usage.windowSeconds()));
            counts.add(MultiGauge.Row.of(tags, usage.count()));
            limits.add(MultiGauge.Row.of(tags, usage.limit()));
        }
        windowCount.register(counts, true);
        windowLimit.register(limits, true);

        List<LimitSpec> appSpecs = LimitSpec.parseAll(engine.appLimit());
        List<MultiGauge.Row<?>> peaks = new ArrayList<>();
        List<MultiGauge.Row<?>> ratios = new ArrayList<>();
        for (String routing : seenRoutings) {
            for (LimitSpec spec : appSpecs) {
                Tags tags = Tags.of("routing", routing, "window", String.valueOf(spec.windowSeconds()));
                int peak = tracker.peak(routing, spec.windowSeconds());
                peaks.add(MultiGauge.Row.of(tags, peak));
                ratios.add(MultiGauge.Row.of(tags, spec.limit() == 0 ? 0.0 : (double) peak / spec.limit()));
            }
        }
        slidingPeak.register(peaks, true);
        slidingPeakRatio.register(ratios, true);
    }
}
