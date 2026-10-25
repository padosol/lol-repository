package com.mmrtr.lol.domain.match.application.port;

import com.mmrtr.lol.domain.match.readmodel.GameReadModel;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 갱신 직후 {@link MatchCacheWritePort} 가 Redis 에 먼저 써 둔 매치 읽기. DB 일괄 저장이 끝나기 전의 최신 매치를
 * 전적 첫 페이지에 보여주는 데 쓴다. Redis 장애·깨진 값은 없는 것으로 본다.
 */
public interface MatchCacheReadPort {

    /** 갱신을 요청한 소환사의 최근 매치 ID, 게임 생성 시각 최신순 (최대 20개, 3분). 없으면 빈 목록. */
    List<String> findRecentMatchIds(String puuid);

    /** 매치 ID → 매치. 캐시에 없는 매치는 결과에 없다. 티어·골드 타임라인처럼 DB 에서만 채우는 값은 비어 있다. */
    Map<String, GameReadModel> findGames(Collection<String> matchIds);
}
