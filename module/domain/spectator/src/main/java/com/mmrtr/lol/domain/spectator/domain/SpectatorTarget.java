package com.mmrtr.lol.domain.spectator.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 관전 조회 대상인 저장된 소환사.
 *
 * @param platformId   저장된 platform_id (KR, NA1 …)
 * @param revisionDate Riot 정보 갱신 일시 (저장할 때와 같이 시스템 시간대 기준)
 */
public record SpectatorTarget(String platformId, LocalDateTime revisionDate) {

    /** 게임 시작 뒤에 소환사 정보가 갱신됐으면 그 게임은 끝났을 수 있다 (lol-server 캐시 무효화 기준). */
    public boolean renewedAfter(long gameStartTimeMillis) {
        return revisionDate != null && revisionDate.isAfter(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(gameStartTimeMillis), ZoneId.systemDefault()));
    }
}
