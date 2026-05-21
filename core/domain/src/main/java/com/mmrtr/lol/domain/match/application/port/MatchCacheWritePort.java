package com.mmrtr.lol.domain.match.application.port;

import com.mmrtr.lol.domain.match.readmodel.MatchDto;

/**
 * lol-server 가 즉시 조회 가능하도록 매치 데이터를 Redis 에 write-through 하기 위한 outbound port.
 * <p>
 * lol-repository 가 Riot API 로부터 매치를 파싱한 직후 호출되며, 단건 매치 캐시와
 * 참가자 별 매치 ID 정렬 셋(ZSET) 을 한 번의 pipeline 으로 기록한다. 실패해도
 * 저장 파이프라인에는 영향을 주지 않는 best-effort 동작이다.
 */
public interface MatchCacheWritePort {

    /**
     * 단건 매치(`match:v1:{matchId}`) 와 참가자 별 매치 ID ZSET(`match:ids:v1:{puuid}`) 을
     * Redis 에 기록한다. 호출은 best-effort 이며 예외를 호출자에게 전파하지 않는다.
     *
     * @param match Riot API 에서 받아 파싱한 매치 DTO (metadata.matchId, info.gameCreation,
     *              info.participants[].puuid 필요)
     */
    void writeMatch(MatchDto match);
}
