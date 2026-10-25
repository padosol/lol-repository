package com.mmrtr.lol.domain.league.application.usecase;

import com.mmrtr.lol.domain.league.domain.RankingQueue;
import com.mmrtr.lol.domain.league.readmodel.RankingReadModel;
import com.mmrtr.lol.domain.league.readmodel.TierCutoffReadModel;
import com.mmrtr.lol.support.page.PageResult;

import java.util.List;

/** 공개 API {@code /api/v1/rankings/**} 조회 (공개 API 재설계 3.3절). */
public interface RankingQueryUseCase {

    /**
     * 리더보드. 페이지당 50명.
     *
     * @param platform 플랫폼 이름 (예: kr). 모르는 값은 INVALID_INPUT
     * @param tier 선택 필터 (대소문자 무시)
     * @param page 1부터. 1 미만은 INVALID_INPUT
     */
    PageResult<RankingReadModel> getRankings(String platform, RankingQueue queue, String tier, int page);

    /**
     * 티어 컷오프 목록. 조건에 맞는 데이터가 없으면 빈 목록.
     *
     * @param queue 선택 필터 (대소문자 무시, 예: RANKED_SOLO_5x5)
     * @param tier 선택 필터 (대소문자 무시). queue 없이 주면 INVALID_INPUT
     */
    List<TierCutoffReadModel> getTierCutoffs(String platform, String queue, String tier);
}
