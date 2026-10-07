package com.mmrtr.lol.domain.champion.application.usecase;

import com.mmrtr.lol.domain.champion.domain.ChampionRotation;

public interface ChampionRotationQueryUseCase {

    /** 캐시 우선 조회. 없는 플랫폼이면 {@code INVALID_INPUT}. */
    ChampionRotation getChampionRotation(String platform);

    /** 로테이션은 매주 화요일 갱신되므로 주간 스케줄러가 호출한다. */
    void evictChampionRotations();
}
