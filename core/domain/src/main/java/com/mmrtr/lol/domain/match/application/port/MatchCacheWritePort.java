package com.mmrtr.lol.domain.match.application.port;

import com.mmrtr.lol.domain.match.readmodel.MatchDto;

import java.util.List;

/**
 * lol-server 가 즉시 조회 가능하도록 매치 데이터를 Redis 에 write-through 하기 위한 outbound port.
 * <p>
 * 갱신을 요청한 본인 puuid 의 ZSET 에만 매치 ID 를 추가한다. 다른 참가자의 ZSET 은 건드리지 않아
 * "최근 갱신 유저만 캐시 hit" 정책을 유지한다. 단건 매치 캐시는 모든 유저가 공유하므로 그대로 SET 한다.
 * 실패해도 저장 파이프라인에는 영향을 주지 않는 best-effort 동작이다.
 */
public interface MatchCacheWritePort {

    /**
     * 단건 매치(`match:v1:{matchId}`) N 개와 요청자 puuid 의 매치 ID ZSET(`match:ids:v1:{requesterPuuid}`) 을
     * 한 번의 Redis pipeline 으로 기록한다. 호출은 best-effort 이며 예외를 호출자에게 전파하지 않는다.
     *
     * @param matches         Riot API 에서 받아 파싱한 매치 DTO 목록 (각 metadata.matchId, info.gameCreation 필요)
     * @param requesterPuuid  갱신을 요청한 유저의 puuid. 이 puuid 의 ZSET 에만 매치 ID 가 추가된다.
     */
    void writeMatches(List<MatchDto> matches, String requesterPuuid);
}
