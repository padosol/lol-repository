package com.mmrtr.lol.domain.champion.application.port;

import com.mmrtr.lol.domain.champion.domain.ChampionRotation;
import java.util.Optional;

public interface ChampionRotationCachePort {

    Optional<ChampionRotation> find(String platform);

    /** 빈 로테이션은 어댑터가 짧은 TTL(negative cache)로 저장한다. */
    void save(String platform, ChampionRotation rotation);
}
