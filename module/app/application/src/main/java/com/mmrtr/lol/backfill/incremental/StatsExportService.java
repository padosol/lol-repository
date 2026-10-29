package com.mmrtr.lol.backfill.incremental;

import com.mmrtr.lol.backfill.BackfillFactType;
import com.mmrtr.lol.backfill.incremental.JobRunOutcome.Status;
import com.mmrtr.lol.backfill.incremental.TargetPatchPolicy.Hold;
import com.mmrtr.lol.backfill.incremental.TargetPatchPolicy.Proceed;
import com.mmrtr.lol.backfill.incremental.TargetPatchPolicy.TargetDecision;
import com.mmrtr.lol.backfill.incremental.TickReport.FactOutcome;
import com.mmrtr.lol.backfill.incremental.TickReport.FactReport;
import com.mmrtr.lol.domain.championstats.application.usecase.ChampionStatsCacheEvictUseCase;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 틱 1회: 락 - 고아 정리 - fact 별(워터마크 - 대상 패치 - 잡 - 전진) - 캐시 무효화.
 * 적재 성공(또는 적재할 것이 없음)이 확인되기 전에는 워터마크를 움직이지 않는다.
 */
@Slf4j
@Service
public class StatsExportService {

    static final String LOCK_NAME = "stats-export:lock";
    private static final List<BackfillFactType> FACT_ORDER = List.of(
            BackfillFactType.MATCH, BackfillFactType.MATCH_BAN, BackfillFactType.MATCH_PARTICIPANT_BUILD);

    private final RedissonClient redisson;
    private final StatsExportProperties props;
    private final StaleExecutionCleaner staleCleaner;
    private final StatsExportSourceQueries sourceQueries;
    private final StatsExportWatermarkRepository repo;
    private final StatsExportJobRunner runner;
    private final ChampionStatsCacheEvictUseCase evictUseCase;
    private final StatsExportMetrics metrics;

    private final Map<BackfillFactType, Integer> consecutiveHolds = new ConcurrentHashMap<>();
    private volatile TickReport lastReport;

    public StatsExportService(
            RedissonClient redisson,
            StatsExportProperties props,
            StaleExecutionCleaner staleCleaner,
            StatsExportSourceQueries sourceQueries,
            StatsExportWatermarkRepository repo,
            StatsExportJobRunner runner,
            ChampionStatsCacheEvictUseCase evictUseCase,
            StatsExportMetrics metrics
    ) {
        this.redisson = redisson;
        this.props = props;
        this.staleCleaner = staleCleaner;
        this.sourceQueries = sourceQueries;
        this.repo = repo;
        this.runner = runner;
        this.evictUseCase = evictUseCase;
        this.metrics = metrics;
    }

    public Optional<TickReport> lastReport() {
        return Optional.ofNullable(lastReport);
    }

    public TickReport runTick() {
        RLock lock = redisson.getLock(LOCK_NAME);
        if (!lock.tryLock()) {
            log.info("stats export 틱 건너뜀: 다른 인스턴스가 락을 보유 중");
            return TickReport.skippedReport();
        }
        try {
            staleCleaner.markStaleExecutionsFailed();
            List<PatchVersion> table = sourceQueries.latestSeasonPatches(props.patchCount());
            List<FactReport> reports = new ArrayList<>();
            for (BackfillFactType factType : FACT_ORDER) {
                reports.add(processFact(factType, table));
            }
            boolean evict = reports.stream().anyMatch(r ->
                    (r.outcome() == FactOutcome.LOADED && r.rowsLoaded() > 0)
                            || r.outcome() == FactOutcome.ALREADY_COMPLETE);
            if (evict) {
                long evicted = evictUseCase.evictAll();
                log.info("챔피언 통계 캐시 무효화: {}건", evicted);
            }
            TickReport report = new TickReport(false, List.copyOf(reports), evict, Instant.now());
            lastReport = report;
            return report;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private FactReport processFact(BackfillFactType factType, List<PatchVersion> table) {
        FactReport report;
        try {
            report = doFact(factType, table);
        } catch (Exception e) {
            log.error("stats export fact 처리 중 예외: fact={}", factType.code(), e);
            report = new FactReport(factType, FactOutcome.FAILED, 0, 0, 0, "예외: " + e.getMessage());
        }
        metrics.recordRun(factType, report.outcome());
        return report;
    }

    private FactReport doFact(BackfillFactType f, List<PatchVersion> table) {
        Watermark w = repo.findOrCreate(f, props.seedId());
        long start = w.exportedUntil();
        long end = w.observedUntil();
        long nowMax = sourceQueries.nextId(f);
        metrics.recordLag(f, nowMax - start);

        if (end <= start) {
            repo.advance(f, start, end, nowMax, null);
            consecutiveHolds.remove(f);
            return new FactReport(f, FactOutcome.ADVANCED_EMPTY, start, end, 0, "observed_until=" + nowMax);
        }

        List<PatchVersion> patches;
        if (w.rangePatches() != null) {
            patches = PatchVersion.split(w.rangePatches());
        } else {
            TargetDecision decision = TargetPatchPolicy.decide(
                    table, sourceQueries.maxPatchInRange(f, start, end, props.queueIds()));
            if (decision instanceof Hold hold) {
                int count = consecutiveHolds.merge(f, 1, Integer::sum);
                if (count == 1) {
                    log.warn("stats export 보류: fact={}, reason={}", f.code(), hold.reason());
                } else {
                    log.error("stats export 연속 {}회 보류: fact={}, reason={}", count, f.code(), hold.reason());
                }
                return new FactReport(f, FactOutcome.HELD, start, end, 0, hold.reason());
            }
            patches = ((Proceed) decision).patches();
            repo.saveRangePatches(f, start, end, PatchVersion.join(patches));
        }
        consecutiveHolds.remove(f);

        if (patches.isEmpty()) {
            repo.advance(f, start, end, nowMax, null);
            return new FactReport(f, FactOutcome.NO_TARGET_ROWS, start, end, 0, "대상 패치 행 없음");
        }

        int season = patches.get(0).major();
        metrics.recordTargetPatch(patches.get(0));
        if (f == BackfillFactType.MATCH_PARTICIPANT_BUILD && !sourceQueries.referenceData(season).complete()) {
            log.error("시즌 기준 데이터(item_meta)가 불완전하여 export 불가: season={}", season);
            return new FactReport(f, FactOutcome.FAILED, start, end, 0, "시즌 " + season + " 기준 데이터 불완전");
        }

        JobRunOutcome outcome = runner.run(new StatsExportRequest(
                f, start, end, season, patches.stream().map(PatchVersion::value).toList(), props.queueIds()));
        if (outcome.status() == Status.FAILED) {
            log.error("stats export 잡 실패: fact={}, runId={}", f.code(), outcome.runId());
            return new FactReport(f, FactOutcome.FAILED, start, end, 0, "잡 실패: " + outcome.runId());
        }

        if (!repo.advance(f, start, end, nowMax, outcome.runId())) {
            log.warn("워터마크 전진 실패(낙관적 락 불일치): fact={}, range=[{}, {})", f.code(), start, end);
        }
        metrics.recordLastSuccess(f, Instant.now().getEpochSecond());
        if (outcome.status() == Status.LOADED) {
            metrics.recordRows(f, outcome.rowsLoaded());
            return new FactReport(f, FactOutcome.LOADED, start, end, outcome.rowsLoaded(), outcome.runId());
        }
        return new FactReport(f, FactOutcome.ALREADY_COMPLETE, start, end, 0, outcome.runId());
    }
}
