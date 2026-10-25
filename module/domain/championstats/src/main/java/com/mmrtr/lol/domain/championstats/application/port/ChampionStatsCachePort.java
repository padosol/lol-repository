package com.mmrtr.lol.domain.championstats.application.port;

import com.mmrtr.lol.domain.championstats.readmodel.ChampionStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.PositionChampionStatsReadModel;

import java.util.List;
import java.util.Optional;

/**
 * 통계 캐시와 single-flight 락. 캐시 장애는 미스, 저장 실패는 무시한다 (어댑터 책임).
 * 락은 잡은 스레드에서만 풀린다 — {@code tryLock*} 와 {@code unlock*} 는 같은 스레드에서 부른다.
 */
public interface ChampionStatsCachePort {

    Optional<ChampionStatsReadModel> findChampionStats(int championId, String patch, String platformId, String tierDisplay);

    void saveChampionStats(int championId, String patch, String platformId, String tierDisplay, ChampionStatsReadModel stats);

    /** 빈 목록도 캐시된 값이다 — 미스는 {@code Optional.empty()}. */
    Optional<List<PositionChampionStatsReadModel>> findChampionStatsByPosition(
            String patch, String platformId, String tierDisplay);

    void saveChampionStatsByPosition(
            String patch, String platformId, String tierDisplay, List<PositionChampionStatsReadModel> stats);

    boolean tryLockDetail(int championId, String patch, String platformId, String tierDisplay);

    void unlockDetail(int championId, String patch, String platformId, String tierDisplay);

    boolean tryLockByPosition(String patch, String platformId, String tierDisplay);

    void unlockByPosition(String patch, String platformId, String tierDisplay);
}
