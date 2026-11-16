package com.mmrtr.lol.backfill.incremental;

import com.mmrtr.lol.backfill.BackfillFactType;
import com.mmrtr.lol.backfill.incremental.TickReport.FactOutcome;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * stats export 파이프라인 메트릭.
 */
@Component
public class StatsExportMetrics {

    private final MeterRegistry registry;
    private final Map<BackfillFactType, AtomicLong> lag = new ConcurrentHashMap<>();
    private final Map<BackfillFactType, AtomicLong> lastSuccess = new ConcurrentHashMap<>();
    private final AtomicLong targetPatch = new AtomicLong();
    private boolean targetPatchRegistered;

    public StatsExportMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordRun(BackfillFactType factType, FactOutcome outcome) {
        registry.counter("stats.export.runs", "fact", factType.code(),
                "outcome", outcome.name().toLowerCase(Locale.ROOT)).increment();
    }

    public void recordRows(BackfillFactType factType, long rows) {
        if (rows > 0) {
            registry.counter("stats.export.rows", "fact", factType.code()).increment(rows);
        }
    }

    public void recordLag(BackfillFactType factType, long lagIds) {
        gauge(lag, "stats.export.lag.ids", factType).set(lagIds);
    }

    public void recordLastSuccess(BackfillFactType factType, long epochSeconds) {
        gauge(lastSuccess, "stats.export.last.success", factType).set(epochSeconds);
    }

    public synchronized void recordTargetPatch(PatchVersion patch) {
        if (!targetPatchRegistered) {
            Gauge.builder("stats.export.target.patch", targetPatch, AtomicLong::get).register(registry);
            targetPatchRegistered = true;
        }
        targetPatch.set(patch.major() * 100L + patch.minor());
    }

    private AtomicLong gauge(Map<BackfillFactType, AtomicLong> holder, String name, BackfillFactType factType) {
        return holder.computeIfAbsent(factType, f -> {
            AtomicLong value = new AtomicLong();
            Gauge.builder(name, value, AtomicLong::get).tag("fact", f.code()).register(registry);
            return value;
        });
    }
}
