package com.mmrtr.lol.domain.championstats.application.usecase;

import com.mmrtr.lol.domain.championstats.readmodel.ChampionStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.PositionChampionStatsReadModel;

import java.util.List;

/**
 * {@code platform} 은 플랫폼 이름({@code kr}), {@code patch} 는 {@code 14.24} 형식,
 * {@code tier} 는 단일 티어({@code EMERALD}) 또는 범위({@code MASTER+}).
 * 모르는 platform·patch 형식은 {@code INVALID_INPUT}, 잘못된 tier 는 {@code INVALID_TIER_FILTER}.
 */
public interface ChampionStatsQueryUseCase {

    ChampionStatsReadModel getChampionStats(int championId, String platform, String patch, String tier);

    List<PositionChampionStatsReadModel> getChampionStatsByPosition(String platform, String patch, String tier);
}
