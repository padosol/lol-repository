package com.mmrtr.lol.infra.redis.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmrtr.lol.domain.champion.application.port.ChampionRotationCachePort;
import com.mmrtr.lol.domain.champion.domain.ChampionRotation;
import com.mmrtr.lol.domain.gamedata.application.port.VersionCachePort;
import com.mmrtr.lol.domain.gamedata.readmodel.VersionReadModel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 게임 데이터 캐시 (최신 버전·챔피언 로테이션). 값은 JSON 문자열.
 *
 * <p>키는 lol-server 의 {@code version:latest:v2}·{@code champion_rotation_*} 와 겹치지 않게 {@code gamedata:} 로 둔다.
 * lol-server 는 {@code @class} 타입 정보가 붙은 JSON 을 쓰므로 같은 키를 공유하면 서로 역직렬화에 실패한다.
 * 키가 갈려 있어 전환 시 flush 가 필요 없고, lol-server 쪽 옛 키는 TTL(최대 24시간)로 사라진다.
 *
 * <p>Redis 장애는 캐시 미스로 취급한다 — 조회는 DB/Riot 으로 넘어가고 저장 실패는 로그만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameDataCacheRedisAdapter implements VersionCachePort, ChampionRotationCachePort {

    static final String LATEST_VERSION_KEY = "gamedata:version:latest";
    static final String CHAMPION_ROTATION_KEY_PREFIX = "gamedata:champion-rotation:";

    private static final Duration LATEST_VERSION_TTL = Duration.ofHours(24);
    private static final Duration CHAMPION_ROTATION_TTL = Duration.ofHours(1);
    // 빈 로테이션(Riot 조회 실패)은 짧게만 캐싱한다. 캐싱하지 않으면 매 요청이 Riot 으로 관통해 rate limit 을
    // 소모하고, 1시간을 캐싱하면 복구된 뒤에도 빈 값이 고착된다.
    private static final Duration EMPTY_CHAMPION_ROTATION_TTL = Duration.ofSeconds(60);
    private static final int SCAN_COUNT = 100;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<VersionReadModel> findLatestVersion() {
        return read(LATEST_VERSION_KEY, VersionReadModel.class);
    }

    @Override
    public void saveLatestVersion(VersionReadModel version) {
        if (version != null) {
            write(LATEST_VERSION_KEY, version, LATEST_VERSION_TTL);
        }
    }

    @Override
    public Optional<ChampionRotation> find(String platform) {
        return read(rotationKey(platform), ChampionRotationView.class).map(ChampionRotationView::toDomain);
    }

    @Override
    public void save(String platform, ChampionRotation rotation) {
        Duration ttl = rotation.isEmpty() ? EMPTY_CHAMPION_ROTATION_TTL : CHAMPION_ROTATION_TTL;
        write(rotationKey(platform), ChampionRotationView.from(rotation), ttl);
    }

    /** 어떤 플랫폼이 캐시돼 있는지 미리 알 수 없어 KEYS 대신 SCAN 으로 모아 지운다. */
    @Override
    public void evictAll() {
        ScanOptions options = ScanOptions.scanOptions()
                .match(CHAMPION_ROTATION_KEY_PREFIX + "*")
                .count(SCAN_COUNT)
                .build();
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (keys.isEmpty()) {
            log.info("Champion rotation cache evict - 삭제 대상 키 없음");
            return;
        }
        Long deleted = redisTemplate.delete(keys);
        log.info("Champion rotation cache evicted - 대상: {}, 삭제: {}", keys.size(), deleted);
    }

    private String rotationKey(String platform) {
        return CHAMPION_ROTATION_KEY_PREFIX + platform.toLowerCase();
    }

    private <T> Optional<T> read(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, type));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("cache read failed - key: {}, cause: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private void write(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("cache write failed - key: {}, cause: {}", key, e.getMessage());
        }
    }

    record ChampionRotationView(
            List<Integer> freeChampionIds,
            List<Integer> freeChampionIdsForNewPlayers,
            int maxNewPlayerLevel
    ) {
        static ChampionRotationView from(ChampionRotation rotation) {
            return new ChampionRotationView(
                    rotation.getFreeChampionIds(),
                    rotation.getFreeChampionIdsForNewPlayers(),
                    rotation.getMaxNewPlayerLevel());
        }

        ChampionRotation toDomain() {
            return new ChampionRotation(freeChampionIds, freeChampionIdsForNewPlayers, maxNewPlayerLevel);
        }
    }
}
