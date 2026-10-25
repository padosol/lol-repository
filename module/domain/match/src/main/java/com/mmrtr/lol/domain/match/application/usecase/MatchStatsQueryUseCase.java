package com.mmrtr.lol.domain.match.application.usecase;

import com.mmrtr.lol.domain.match.readmodel.DailyGameCountReadModel;
import com.mmrtr.lol.domain.match.readmodel.RankChampionsReadModel;

/** 소환사별 매치 통계 — lol-server match 모듈 daily-count·랭크 챔피언 API 의 이전 (공개 API 재설계 3.1절). */
public interface MatchStatsQueryUseCase {

    /**
     * 최근 3개월(오늘 0시 기준) 날짜별 게임 수.
     *
     * @param queueId null 이면 솔로·자유 랭크
     */
    DailyGameCountReadModel getDailyGameCounts(String puuid, int season, Integer queueId);

    /**
     * 솔로·자유 랭크 챔피언별 통계.
     *
     * @param season null 이면 전 시즌 (lol-server 는 500)
     */
    RankChampionsReadModel getRankChampions(String puuid, Integer season);
}
