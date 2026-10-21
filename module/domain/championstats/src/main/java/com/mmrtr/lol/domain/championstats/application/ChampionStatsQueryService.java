package com.mmrtr.lol.domain.championstats.application;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.championstats.application.port.ChampionStatsCachePort;
import com.mmrtr.lol.domain.championstats.application.port.ChampionStatsMetricsPort;
import com.mmrtr.lol.domain.championstats.application.port.ChampionStatsQueryPort;
import com.mmrtr.lol.domain.championstats.application.usecase.ChampionStatsQueryUseCase;
import com.mmrtr.lol.domain.championstats.domain.TierFilter;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionAverageStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionBootBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionItemBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionMatchupReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionPositionStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionRateReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionRuneBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionSkillBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionSpellStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionStartItemBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionWinRateReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.PositionChampionStatsReadModel;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 챔피언 통계 조회. lol-server {@code ChampionStatsService} 이전.
 *
 * <p>목록·상세 모두 캐시 우선이고, 미스는 single-flight 로 묶는다 — 락을 잡은 요청만 BigQuery 를 부르고
 * 나머지는 캐시가 채워지길 기다렸다가(200ms × 15) 끝내 없으면 직접 부른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChampionStatsQueryService implements ChampionStatsQueryUseCase {

    static final int CACHE_POLL_ATTEMPTS = 15;
    static final long CACHE_POLL_INTERVAL_MS = 200L;

    /** BigQuery 의 {@code patch_version_int}(major × 100 + minor)로 바꿀 수 있는 형식. */
    private static final Pattern PATCH_FORMAT = Pattern.compile("\\d{1,4}\\.\\d{1,2}");

    private final ChampionStatsQueryPort championStatsQueryPort;
    private final ChampionStatsCachePort championStatsCachePort;
    private final ChampionStatsMetricsPort championStatsMetricsPort;
    private final Executor requestExecutor;

    @Override
    public ChampionStatsReadModel getChampionStats(int championId, String platform, String patch, String tier) {
        String platformId = toPlatformId(platform);
        validatePatch(patch);
        TierFilter tierFilter = toTierFilter(tier);
        String tierDisplay = tierFilter.toDisplayString();

        return loadWithSingleFlight(
                () -> championStatsCachePort.findChampionStats(championId, patch, platformId, tierDisplay),
                () -> championStatsCachePort.tryLockDetail(championId, patch, platformId, tierDisplay),
                () -> championStatsCachePort.unlockDetail(championId, patch, platformId, tierDisplay),
                () -> computeChampionStats(championId, patch, platformId, tierFilter),
                value -> championStatsCachePort.saveChampionStats(championId, patch, platformId, tierDisplay, value),
                ChampionStatsMetricsPort.ENDPOINT_DETAIL);
    }

    @Override
    public List<PositionChampionStatsReadModel> getChampionStatsByPosition(String platform, String patch, String tier) {
        String platformId = toPlatformId(platform);
        validatePatch(patch);
        return loadChampionStatsByPosition(patch, platformId, toTierFilter(tier));
    }

    private List<PositionChampionStatsReadModel> loadChampionStatsByPosition(
            String patch, String platformId, TierFilter tierFilter) {
        String tierDisplay = tierFilter.toDisplayString();

        return loadWithSingleFlight(
                () -> championStatsCachePort.findChampionStatsByPosition(patch, platformId, tierDisplay),
                () -> championStatsCachePort.tryLockByPosition(patch, platformId, tierDisplay),
                () -> championStatsCachePort.unlockByPosition(patch, platformId, tierDisplay),
                () -> computeChampionStatsByPosition(patch, platformId, tierFilter),
                value -> championStatsCachePort.saveChampionStatsByPosition(patch, platformId, tierDisplay, value),
                ChampionStatsMetricsPort.ENDPOINT_POSITIONS);
    }

    private static String toPlatformId(String platform) {
        Platform resolved = Platform.valueOfName(platform);
        if (resolved == null) {
            throw new CoreException(ErrorType.INVALID_INPUT, "유효하지 않은 platform 입니다: " + platform);
        }
        return resolved.getPlatformId();
    }

    private static void validatePatch(String patch) {
        if (patch == null || !PATCH_FORMAT.matcher(patch).matches()) {
            throw new CoreException(ErrorType.INVALID_INPUT, "유효하지 않은 patch 입니다: " + patch);
        }
    }

    private static TierFilter toTierFilter(String tier) {
        try {
            return TierFilter.of(tier);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.INVALID_TIER_FILTER, e.getMessage());
        }
    }

    private ChampionStatsReadModel computeChampionStats(
            int championId, String patch, String platformId, TierFilter tierFilter) {

        CompletableFuture<List<ChampionWinRateReadModel>> winRatesFuture = async(() ->
                championStatsQueryPort.getChampionWinRates(championId, patch, platformId, tierFilter));
        CompletableFuture<Map<String, ChampionRateReadModel>> ratesFuture = async(() ->
                lookupChampionRateByPosition(championId, patch, platformId, tierFilter));
        CompletableFuture<Map<String, ChampionAverageStatsReadModel>> averagesFuture = async(() ->
                championStatsQueryPort.getChampionAverageStats(championId, patch, platformId, tierFilter).stream()
                        .collect(Collectors.toMap(ChampionAverageStatsReadModel::teamPosition, a -> a)));

        // 포지션별 조회를 모두 띄운 뒤에 기다린다
        List<CompletableFuture<ChampionPositionStatsReadModel>> positionFutures = winRatesFuture.join().stream()
                .map(winRate -> buildPositionStatsAsync(
                        championId, patch, platformId, tierFilter, winRate, ratesFuture, averagesFuture))
                .toList();
        List<ChampionPositionStatsReadModel> positions = positionFutures.stream()
                .map(CompletableFuture::join)
                .toList();

        return new ChampionStatsReadModel(tierFilter.toDisplayString(), positions);
    }

    private CompletableFuture<ChampionPositionStatsReadModel> buildPositionStatsAsync(
            int championId, String patch, String platformId, TierFilter tierFilter,
            ChampionWinRateReadModel winRate,
            CompletableFuture<Map<String, ChampionRateReadModel>> ratesFuture,
            CompletableFuture<Map<String, ChampionAverageStatsReadModel>> averagesFuture) {

        String position = winRate.teamPosition();
        long totalSamples = winRate.totalGames();

        CompletableFuture<List<ChampionMatchupReadModel>> matchupsF = async(() ->
                championStatsQueryPort.getChampionMatchups(championId, patch, platformId, tierFilter, position));
        CompletableFuture<List<ChampionRuneBuildReadModel>> runeBuildsF = async(() ->
                withConfidence(championStatsQueryPort.getChampionRuneBuilds(
                                championId, patch, platformId, tierFilter, position),
                        totalSamples, ChampionRuneBuildReadModel::games,
                        ChampionRuneBuildReadModel::winRate, ChampionRuneBuildReadModel::withConfidence));
        CompletableFuture<List<ChampionSpellStatsReadModel>> spellStatsF = async(() ->
                withConfidence(championStatsQueryPort.getChampionSpellStats(
                                championId, patch, platformId, tierFilter, position),
                        totalSamples, ChampionSpellStatsReadModel::games,
                        ChampionSpellStatsReadModel::winRate, ChampionSpellStatsReadModel::withConfidence));
        CompletableFuture<List<ChampionSkillBuildReadModel>> skillBuildsF = async(() ->
                withConfidence(championStatsQueryPort.getChampionSkillBuilds(
                                championId, patch, platformId, tierFilter, position).stream()
                                .filter(b -> b.skillBuild() != null && !b.skillBuild().isBlank())
                                .toList(),
                        totalSamples, ChampionSkillBuildReadModel::games,
                        ChampionSkillBuildReadModel::winRate, ChampionSkillBuildReadModel::withConfidence));
        CompletableFuture<List<ChampionStartItemBuildReadModel>> startItemBuildsF = async(() ->
                withConfidence(championStatsQueryPort.getChampionStartItemBuilds(
                                championId, patch, platformId, tierFilter, position),
                        totalSamples, ChampionStartItemBuildReadModel::games,
                        ChampionStartItemBuildReadModel::winRate, ChampionStartItemBuildReadModel::withConfidence));
        CompletableFuture<List<ChampionBootBuildReadModel>> bootBuildsF = async(() ->
                withConfidence(championStatsQueryPort.getChampionBootBuilds(
                                championId, patch, platformId, tierFilter, position),
                        totalSamples, ChampionBootBuildReadModel::games,
                        ChampionBootBuildReadModel::winRate, ChampionBootBuildReadModel::withConfidence));
        CompletableFuture<List<ChampionItemBuildReadModel>> itemBuildsF = async(() ->
                withConfidence(championStatsQueryPort.getChampionItemBuilds(
                                championId, patch, platformId, tierFilter, position),
                        totalSamples, ChampionItemBuildReadModel::games,
                        ChampionItemBuildReadModel::winRate, ChampionItemBuildReadModel::withConfidence));

        return CompletableFuture.allOf(matchupsF, runeBuildsF, spellStatsF, skillBuildsF,
                        startItemBuildsF, bootBuildsF, itemBuildsF, ratesFuture, averagesFuture)
                .thenApply(ignored -> {
                    ChampionRateReadModel rate = ratesFuture.join().get(position);
                    return new ChampionPositionStatsReadModel(
                            position, winRate.totalWinRate(),
                            rate != null ? rate.pickRate() : 0.0,
                            rate != null ? rate.banRate() : 0.0,
                            rate != null ? rate.tier() : null,
                            winRate.totalGames(),
                            averagesFuture.join().get(position),
                            matchupsF.join(), runeBuildsF.join(), spellStatsF.join(), skillBuildsF.join(),
                            startItemBuildsF.join(), bootBuildsF.join(), itemBuildsF.join());
                });
    }

    private <T> CompletableFuture<T> async(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, requestExecutor);
    }

    private static <T> List<T> withConfidence(
            List<T> source, long totalSamples,
            ToLongFunction<T> gamesGetter,
            ToDoubleFunction<T> winRateGetter,
            ConfidenceApplier<T> applier) {
        return source.stream()
                .map(b -> applier.apply(b, totalSamples,
                        wilson(gamesGetter.applyAsLong(b), winRateGetter.applyAsDouble(b))))
                .toList();
    }

    @FunctionalInterface
    private interface ConfidenceApplier<T> {
        T apply(T source, long totalSamples, double confidence);
    }

    private List<PositionChampionStatsReadModel> computeChampionStatsByPosition(
            String patch, String platformId, TierFilter tierFilter) {
        return championStatsQueryPort.getChampionStatsByPosition(patch, platformId, tierFilter).entrySet().stream()
                .map(entry -> new PositionChampionStatsReadModel(entry.getKey(), assignTiersIfMissing(entry.getValue())))
                .toList();
    }

    /** 상세의 포지션별 픽률·밴률·tier 는 목록 통계(캐시 공유)에서 가져온다. */
    private Map<String, ChampionRateReadModel> lookupChampionRateByPosition(
            int championId, String patch, String platformId, TierFilter tierFilter) {
        Map<String, ChampionRateReadModel> result = new HashMap<>();
        for (PositionChampionStatsReadModel position : loadChampionStatsByPosition(patch, platformId, tierFilter)) {
            position.champions().stream()
                    .filter(rate -> rate.championId() == championId)
                    .findFirst()
                    .ifPresent(rate -> result.put(position.teamPosition(), rate));
        }
        return result;
    }

    private <T> T loadWithSingleFlight(
            Supplier<Optional<T>> cacheLookup,
            BooleanSupplier tryAcquireLock,
            Runnable releaseLock,
            Supplier<T> computeFresh,
            Consumer<T> saveCache,
            String endpoint) {

        Optional<T> cached = cacheLookup.get();
        if (cached.isPresent()) {
            return cached.get();
        }

        if (tryAcquireLock.getAsBoolean()) {
            try {
                // 락을 기다리는 사이 앞선 요청이 캐시를 채웠을 수 있다
                return cacheLookup.get().orElseGet(() -> computeAndSave(computeFresh, saveCache));
            } finally {
                releaseLock.run();
            }
        }

        Optional<T> polled = pollCache(cacheLookup);
        if (polled.isPresent()) {
            return polled.get();
        }

        log.warn("Single-flight fallback - endpoint: {}", endpoint);
        championStatsMetricsPort.recordSingleFlightFallback(endpoint);
        return computeAndSave(computeFresh, saveCache);
    }

    private static <T> Optional<T> pollCache(Supplier<Optional<T>> cacheLookup) {
        for (int attempt = 0; attempt < CACHE_POLL_ATTEMPTS; attempt++) {
            try {
                Thread.sleep(CACHE_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
            Optional<T> cached = cacheLookup.get();
            if (cached.isPresent()) {
                return cached;
            }
        }
        return Optional.empty();
    }

    private static <T> T computeAndSave(Supplier<T> computeFresh, Consumer<T> saveCache) {
        T fresh = computeFresh.get();
        saveCache.accept(fresh);
        return fresh;
    }

    private static double wilson(long games, double winRate) {
        long wins = Math.round(winRate * games);
        return WilsonScore.lowerBound95(wins, games);
    }

    /** BigQuery 가 tier 를 정해 주므로 보통은 그대로 쓴다. 비어 있을 때만 계산한다 (lol-server 와 같다). */
    private static List<ChampionRateReadModel> assignTiersIfMissing(List<ChampionRateReadModel> champions) {
        if (!champions.isEmpty() && champions.get(0).tier() != null) {
            return champions;
        }
        return ChampionTierCalculator.assignTiers(champions);
    }
}
