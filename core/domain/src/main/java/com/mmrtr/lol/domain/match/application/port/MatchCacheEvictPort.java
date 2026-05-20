package com.mmrtr.lol.domain.match.application.port;

import java.util.Set;

/**
 * lol-server 가 보유한 매치 관련 캐시를 무효화하기 위한 outbound port.
 * <p>
 * lol-repository 가 새 매치를 저장한 직후 호출되어, 사용자가 최신 매치를
 * 즉시 볼 수 있도록 한다. 호출은 best-effort 이며 실패해도 저장 트랜잭션은
 * 영향을 받지 않는다.
 */
public interface MatchCacheEvictPort {

    /**
     * 주어진 puuid 들의 매치 리스트 캐시(`match:list:v1:{puuid}:*`) 를 모두 제거한다.
     *
     * @param puuids 영향을 받은 참가자 puuid 집합
     */
    void evictByPuuids(Set<String> puuids);
}
