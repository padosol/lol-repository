package com.mmrtr.lol.domain.league.application.port;

import com.mmrtr.lol.domain.league.domain.SummonerRanking;
import com.mmrtr.lol.domain.league.domain.TierCutoff;
import com.mmrtr.lol.support.page.PageResult;

import java.util.List;

/** 공개 API 용 랭킹·티어 컷오프 조회. 쓰기 쪽 port({@link SummonerRankingRepositoryPort} 등)와 나눠 둔다. */
public interface RankingQueryPort {

    /**
     * 리더보드 한 페이지. 순위({@code currentRank}) 오름차순.
     *
     * @param tier null 이면 전체 티어
     * @param page 1부터 시작
     */
    PageResult<SummonerRanking> findRankings(String platformId, String queue, String tier, int page, int size);

    /**
     * 티어 컷오프 목록. id 오름차순.
     *
     * @param queue null 이면 전체 큐
     * @param tier null 이면 전체 티어
     */
    List<TierCutoff> findTierCutoffs(String platformId, String queue, String tier);
}
