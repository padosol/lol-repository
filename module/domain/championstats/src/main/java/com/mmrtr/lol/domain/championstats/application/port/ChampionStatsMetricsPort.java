package com.mmrtr.lol.domain.championstats.application.port;

public interface ChampionStatsMetricsPort {

    String ENDPOINT_DETAIL = "detail";
    String ENDPOINT_POSITIONS = "positions";

    /** 락을 못 잡고 캐시 대기도 끝나 직접 BigQuery 를 부른 횟수. */
    void recordSingleFlightFallback(String endpoint);
}
