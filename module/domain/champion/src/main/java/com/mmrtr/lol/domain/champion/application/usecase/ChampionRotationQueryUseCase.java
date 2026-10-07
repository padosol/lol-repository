package com.mmrtr.lol.domain.champion.application.usecase;

import com.mmrtr.lol.domain.champion.domain.ChampionRotation;

public interface ChampionRotationQueryUseCase {

    /** 캐시 우선 조회. 없는 플랫폼이면 {@code INVALID_INPUT}. */
    ChampionRotation getChampionRotation(String platform);
}
