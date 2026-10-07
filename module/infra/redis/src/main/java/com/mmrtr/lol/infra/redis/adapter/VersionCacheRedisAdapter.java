package com.mmrtr.lol.infra.redis.adapter;

import com.mmrtr.lol.domain.gamedata.application.port.VersionCachePort;
import com.mmrtr.lol.domain.gamedata.readmodel.VersionReadModel;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 최신 패치 버전 캐시 (gamedata 컨텍스트). lol-server 의 {@code version:latest:v2} 와 키를 공유하지 않는다.
 *
 * <p>TTL 을 10분으로 둔다 — 버전 행을 쓰는 쪽(크롤러·admin)이 이 캐시를 무효화하지 않으므로,
 * 패치 당일 {@code /versions/latest} 가 {@code /versions}(캐시 없음)와 어긋나는 시간을 10분 안으로 묶는다.
 */
@Component
@RequiredArgsConstructor
public class VersionCacheRedisAdapter implements VersionCachePort {

    static final String LATEST_VERSION_KEY = "gamedata:version:latest";
    static final Duration LATEST_VERSION_TTL = Duration.ofMinutes(10);

    private final JsonValueCache cache;

    @Override
    public Optional<VersionReadModel> findLatestVersion() {
        return cache.get(LATEST_VERSION_KEY, VersionReadModel.class);
    }

    @Override
    public void saveLatestVersion(VersionReadModel version) {
        if (version != null) {
            cache.set(LATEST_VERSION_KEY, version, LATEST_VERSION_TTL);
        }
    }
}
