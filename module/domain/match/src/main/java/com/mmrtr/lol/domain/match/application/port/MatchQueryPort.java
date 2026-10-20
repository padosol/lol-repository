package com.mmrtr.lol.domain.match.application.port;

import com.mmrtr.lol.domain.match.readmodel.GameReadModel.GameInfo;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.ParticipantRecord;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.Event;

import java.util.List;
import java.util.Optional;

/** 공개 매치 조회 (`/api/v1/matches/**`). 매치·참가자·타임라인 테이블을 읽는 조회 전용 포트. */
public interface MatchQueryPort {

    Optional<GameInfo> findGameInfo(String matchId);

    /**
     * 매치들의 참가자, 매치·참가자 번호순. 이름은 저장된 소환사({@code summoner})의 현재 Riot ID 를 우선한다
     * (lol-server 와 같다 — 게임 뒤 이름을 바꾼 소환사도 현재 이름으로 보인다).
     */
    List<ParticipantRecord> findParticipants(List<String> matchIds);

    /** 매치들의 아이템 구매·스킬 레벨업 이벤트, 프레임 시각·이벤트 순. */
    List<Event> findTimelineEvents(List<String> matchIds);
}
