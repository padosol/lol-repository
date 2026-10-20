package com.mmrtr.lol.domain.match.application.port;

import com.mmrtr.lol.domain.match.readmodel.GameReadModel.GameInfo;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.ParticipantRecord;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.Event;
import com.mmrtr.lol.support.page.SliceResult;

import java.util.List;
import java.util.Optional;

/** 공개 매치 조회 (`/api/v1/matches/**`·소환사별 전적). 매치·참가자·타임라인 테이블을 읽는 조회 전용 포트. */
public interface MatchQueryPort {

    Optional<GameInfo> findGameInfo(String matchId);

    /**
     * 소환사가 참가한 소환사의 협곡·아레나 매치({@code game_mode} CLASSIC·CHERRY), 게임 종료 시각 최신순.
     *
     * @param season  null 이면 전 시즌
     * @param queueId null 이면 전 큐
     * @param page    0부터
     */
    SliceResult<GameInfo> findSummonerGameInfos(String puuid, Integer season, Integer queueId, int page, int size);

    /**
     * 소환사가 참가한 매치 ID, 매치 ID 내림차순. 게임 모드는 거르지 않는다 (lol-server 와 같다).
     *
     * @param queueId null 이면 전 큐
     * @param page    0부터
     */
    SliceResult<String> findSummonerMatchIds(String puuid, Integer queueId, int page, int size);

    /**
     * 매치들의 참가자, 매치·참가자 번호순. 이름은 저장된 소환사({@code summoner})의 현재 Riot ID 를 우선한다
     * (lol-server 와 같다 — 게임 뒤 이름을 바꾼 소환사도 현재 이름으로 보인다).
     */
    List<ParticipantRecord> findParticipants(List<String> matchIds);

    /** 매치들의 아이템 구매·스킬 레벨업 이벤트, 프레임 시각·이벤트 순. */
    List<Event> findTimelineEvents(List<String> matchIds);
}
