package com.mmrtr.lol.domain.match.application.usecase;

import com.mmrtr.lol.domain.match.readmodel.GameReadModel;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel;
import com.mmrtr.lol.support.page.SliceResult;

/** 공개 매치 조회 — lol-server match 모듈 조회 API 의 이전 (공개 API 재설계 3.1·3.2절). */
public interface MatchQueryUseCase {

    /**
     * 저장된 매치. Riot 을 호출하지 않는다.
     *
     * @throws com.mmrtr.lol.support.error.CoreException {@code NOT_FOUND_MATCH_ID}
     */
    GameReadModel getGame(String matchId);

    /** 저장된 타임라인. 없는 매치·타임라인이면 빈 응답 (lol-server 와 같이 404 가 아니다). */
    MatchTimelineReadModel getTimeline(String matchId);

    /**
     * 소환사별 전적, 한 페이지 20건 게임 종료 시각 최신순. 첫 페이지에는 갱신 직후 아직 DB 에 없는 최신 매치를 캐시에서 얹는다.
     *
     * @param page 0부터
     * @throws com.mmrtr.lol.support.error.CoreException {@code INVALID_INPUT}(음수 page)
     */
    SliceResult<GameReadModel> getSummonerMatches(String puuid, Integer season, Integer queueId, int page);

    /**
     * 소환사별 매치 ID, 한 페이지 20건 매치 ID 내림차순.
     *
     * @param page 0부터
     * @throws com.mmrtr.lol.support.error.CoreException {@code INVALID_INPUT}(음수 page)
     */
    SliceResult<String> getSummonerMatchIds(String puuid, Integer queueId, int page);
}
