package com.mmrtr.lol.domain.match.application.port;

import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;

import java.util.List;

/**
 * lol-server 가 즉시 조회 가능하도록 매치 데이터를 Redis 에 write-through 하기 위한 outbound port.
 * <p>
 * 갱신을 요청한 본인 puuid 의 ZSET 에만 매치 ID 를 추가한다. 다른 참가자의 ZSET 은 건드리지 않아
 * "최근 갱신 유저만 캐시 hit" 정책을 유지한다. 단건 매치 캐시는 모든 유저가 공유하므로 그대로 SET 한다.
 * 갱신 매치 단계는 이 캐시에 쓰면 끝나므로(DB 저장은 비동기), 쓰기가 실패하면 던져 호출한 쪽이 실패로 처리하게 한다.
 */
public interface MatchCacheWritePort {

    /**
     * 단건 매치(`match:v1:{matchId}`) N 개와 요청자 puuid 의 매치 ID ZSET(`match:ids:v1:{requesterPuuid}`) 을
     * 한 번의 Redis pipeline 으로 기록한다. 매치 단건은 lol-server 의 GameReadModel 형태로 직렬화되며,
     * timeline 에서 itemSeq/skillSeq 를 추출해 포함한다. 매치가 없거나 requesterPuuid 가 비면 쓰지 않고 돌아온다.
     *
     * @param matches         Riot API 매치 DTO 목록 (각 metadata.matchId, info 필요)
     * @param timelines       각 매치에 index 정렬된 timeline DTO 목록 (itemSeq/skillSeq 추출용, null 허용)
     * @param requesterPuuid  갱신을 요청한 유저의 puuid. 이 puuid 의 ZSET 에만 매치 ID 가 추가된다.
     * @throws IllegalStateException 직렬화나 Redis 쓰기가 실패하면
     */
    void writeMatches(List<MatchDto> matches, List<TimelineDto> timelines, String requesterPuuid);
}
