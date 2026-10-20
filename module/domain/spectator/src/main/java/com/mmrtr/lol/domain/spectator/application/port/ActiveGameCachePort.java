package com.mmrtr.lol.domain.spectator.application.port;

import com.mmrtr.lol.domain.spectator.readmodel.ActiveGameReadModel;

import java.util.Optional;

/** 진행 중인 게임 캐시. 한 게임을 참여자 전원의 puuid 로 저장해, 같은 게임의 다른 참여자 조회도 Riot 을 부르지 않는다. */
public interface ActiveGameCachePort {

    Optional<ActiveGameReadModel> findGame(String platformId, String puuid);

    /** 참여자 전원의 puuid 로 저장한다. */
    void saveGame(String platformId, ActiveGameReadModel game);

    /** 참여자 전원의 캐시를 지운다. */
    void evictGame(String platformId, ActiveGameReadModel game);

    /** 최근에 게임 중이 아니었는지 (짧게 캐싱한 결과). */
    boolean isNotInGame(String platformId, String puuid);

    void saveNotInGame(String platformId, String puuid);
}
