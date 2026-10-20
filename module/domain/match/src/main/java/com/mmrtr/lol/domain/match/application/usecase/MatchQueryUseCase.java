package com.mmrtr.lol.domain.match.application.usecase;

import com.mmrtr.lol.domain.match.readmodel.GameReadModel;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel;

/** 공개 매치 조회 — lol-server match 모듈 조회 API 의 이전 (공개 API 재설계 3.2절). */
public interface MatchQueryUseCase {

    /**
     * 저장된 매치. Riot 을 호출하지 않는다.
     *
     * @throws com.mmrtr.lol.support.error.CoreException {@code NOT_FOUND_MATCH_ID}
     */
    GameReadModel getGame(String matchId);

    /** 저장된 타임라인. 없는 매치·타임라인이면 빈 응답 (lol-server 와 같이 404 가 아니다). */
    MatchTimelineReadModel getTimeline(String matchId);
}
