package com.mmrtr.lol.domain.championstats.application.usecase;

/** 챔피언 통계 캐시 무효화 (BigQuery 적재 직후 호출). */
public interface ChampionStatsCacheEvictUseCase {

    /** 챔피언 통계 캐시를 모두 비우고 지운 키 수를 돌려준다. 캐시 장애로 예외를 던지지 않는다. */
    long evictAll();
}
