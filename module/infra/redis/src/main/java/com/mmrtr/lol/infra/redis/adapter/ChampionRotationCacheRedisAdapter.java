package com.mmrtr.lol.infra.redis.adapter;

import com.mmrtr.lol.domain.champion.application.port.ChampionRotationCachePort;
import com.mmrtr.lol.domain.champion.domain.ChampionRotation;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 챔피언 로테이션 캐시 (champion 컨텍스트). lol-server 의 {@code champion_rotation_*} 와 키를 공유하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ChampionRotationCacheRedisAdapter implements ChampionRotationCachePort {

    static final String KEY_PREFIX = "gamedata:champion-rotation:";
    static final Duration TTL = Duration.ofHours(1);
    // 빈 로테이션(Riot 조회 실패)은 짧게만 캐싱한다. 캐싱하지 않으면 매 요청이 Riot 으로 관통해 rate limit 을
    // 소모하고, 1시간을 캐싱하면 복구된 뒤에도 빈 값이 고착된다.
    static final Duration EMPTY_TTL = Duration.ofSeconds(60);

    private final JsonValueCache cache;

    @Override
    public Optional<ChampionRotation> find(String platform) {
        return cache.get(key(platform), View.class).map(View::toDomain);
    }

    @Override
    public void save(String platform, ChampionRotation rotation) {
        cache.set(key(platform), View.from(rotation), rotation.isEmpty() ? EMPTY_TTL : TTL);
    }

    private String key(String platform) {
        return KEY_PREFIX + platform.toLowerCase();
    }

    record View(
            List<Integer> freeChampionIds,
            List<Integer> freeChampionIdsForNewPlayers,
            int maxNewPlayerLevel
    ) {
        static View from(ChampionRotation rotation) {
            return new View(
                    rotation.getFreeChampionIds(),
                    rotation.getFreeChampionIdsForNewPlayers(),
                    rotation.getMaxNewPlayerLevel());
        }

        ChampionRotation toDomain() {
            return new ChampionRotation(freeChampionIds, freeChampionIdsForNewPlayers, maxNewPlayerLevel);
        }
    }
}
