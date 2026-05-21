package com.mmrtr.lol.infra.redis.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmrtr.lol.domain.match.application.port.MatchCacheWritePort;
import com.mmrtr.lol.domain.match.readmodel.InfoDto;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.MetadataDto;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBatch;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 매치 파싱 직후 Redis 에 write-through 캐시를 채우는 adapter.
 *
 * <p>저장 키:
 * <ul>
 *   <li>{@code match:v1:{matchId}} - 단건 매치 JSON, 3분 TTL (모든 유저 공유)</li>
 *   <li>{@code match:ids:v1:{requesterPuuid}} - 갱신 요청자 ZSET (score = gameCreation),
 *       크기 20 으로 trim, 3분 TTL. 다른 참가자의 ZSET 은 채우지 않아 "최근 갱신 유저만 캐시" 유지</li>
 * </ul>
 *
 * <p>매치 N 개를 한 번의 {@link RBatch} pipeline 으로 1 RTT 에 처리한다. 실패 시 swallow + 메트릭 기록.
 */
@Slf4j
@Component
public class MatchCacheWriteAdapter implements MatchCacheWritePort {

    private static final String MATCH_KEY_PREFIX = "match:v1:";
    private static final String MATCH_IDS_KEY_PREFIX = "match:ids:v1:";
    private static final Duration MATCH_TTL = Duration.ofMinutes(3);
    private static final Duration MATCH_IDS_TTL = Duration.ofMinutes(3);
    private static final int MATCH_IDS_TRIM_KEEP = 20;

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public MatchCacheWriteAdapter(
            RedissonClient redissonClient,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void writeMatches(List<MatchDto> matches, String requesterPuuid) {
        if (matches == null || matches.isEmpty()) {
            return;
        }
        if (requesterPuuid == null || requesterPuuid.isBlank()) {
            log.warn("cache.match.write skipped: blank requesterPuuid matchCount={}", matches.size());
            meterRegistry.counter("cache.match.write", "result", "skipped").increment();
            return;
        }

        try {
            RBatch batch = redissonClient.createBatch();
            Map<String, Double> zsetMembers = new HashMap<>();

            for (MatchDto match : matches) {
                String matchId = extractMatchId(match);
                if (matchId == null) {
                    continue;
                }
                String json = objectMapper.writeValueAsString(match);
                batch.getBucket(MATCH_KEY_PREFIX + matchId, StringCodec.INSTANCE)
                        .setAsync(json, MATCH_TTL);
                zsetMembers.put(matchId, (double) match.getInfo().getGameCreation());
            }

            if (zsetMembers.isEmpty()) {
                return;
            }

            String zsetKey = MATCH_IDS_KEY_PREFIX + requesterPuuid;
            batch.<String>getScoredSortedSet(zsetKey, StringCodec.INSTANCE).addAllAsync(zsetMembers);
            batch.<String>getScoredSortedSet(zsetKey, StringCodec.INSTANCE)
                    .removeRangeByRankAsync(0, -(MATCH_IDS_TRIM_KEEP + 1));
            batch.getKeys().expireAsync(zsetKey, MATCH_IDS_TTL.toSeconds(), TimeUnit.SECONDS);

            batch.execute();
            meterRegistry.counter("cache.match.write", "result", "success").increment(zsetMembers.size());
        } catch (Exception e) {
            log.warn("cache.match.write failed count={} cause={}", matches.size(), e.getMessage());
            meterRegistry.counter("cache.match.write", "result", "failure").increment(matches.size());
        }
    }

    private String extractMatchId(MatchDto match) {
        if (match == null) {
            return null;
        }
        MetadataDto metadata = match.getMetadata();
        InfoDto info = match.getInfo();
        if (metadata == null || info == null) {
            return null;
        }
        String matchId = metadata.getMatchId();
        if (matchId == null || matchId.isBlank()) {
            return null;
        }
        return matchId;
    }
}
