package com.mmrtr.lol.infra.redis.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmrtr.lol.domain.match.application.port.MatchCacheWritePort;
import com.mmrtr.lol.domain.match.readmodel.InfoDto;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.MetadataDto;
import com.mmrtr.lol.domain.match.readmodel.ParticipantDto;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBatch;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 매치 파싱 직후 Redis 에 write-through 캐시를 채우는 adapter.
 *
 * <p>저장 키:
 * <ul>
 *   <li>{@code match:v1:{matchId}} - 단건 매치 JSON, 1시간 TTL</li>
 *   <li>{@code match:ids:v1:{puuid}} - 참가자 별 최근 매치 ID ZSET (score = gameCreation),
 *       크기 20 으로 trim, 24시간 TTL</li>
 * </ul>
 *
 * <p>모든 작업은 {@link RBatch} pipeline 으로 1 RTT 에 수행되며, 실패 시 swallow + 메트릭 기록
 * (호출자 = 저장 파이프라인에 예외 전파하지 않음).
 */
@Slf4j
@Component
public class MatchCacheWriteAdapter implements MatchCacheWritePort {

    private static final String MATCH_KEY_PREFIX = "match:v1:";
    private static final String MATCH_IDS_KEY_PREFIX = "match:ids:v1:";
    private static final Duration MATCH_TTL = Duration.ofHours(1);
    private static final Duration MATCH_IDS_TTL = Duration.ofHours(24);
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
    public void writeMatch(MatchDto match) {
        if (match == null) {
            return;
        }
        MetadataDto metadata = match.getMetadata();
        InfoDto info = match.getInfo();
        if (metadata == null || info == null) {
            return;
        }
        String matchId = metadata.getMatchId();
        if (matchId == null || matchId.isBlank()) {
            return;
        }
        long gameCreation = info.getGameCreation();
        List<ParticipantDto> participants = info.getParticipants();

        try {
            String json = objectMapper.writeValueAsString(match);

            RBatch batch = redissonClient.createBatch();
            batch.getBucket(MATCH_KEY_PREFIX + matchId, StringCodec.INSTANCE)
                    .setAsync(json, MATCH_TTL);

            if (participants != null) {
                for (ParticipantDto participant : participants) {
                    if (participant == null) {
                        continue;
                    }
                    String puuid = participant.getPuuid();
                    if (puuid == null || puuid.isBlank()) {
                        continue;
                    }
                    String zsetKey = MATCH_IDS_KEY_PREFIX + puuid;
                    batch.getScoredSortedSet(zsetKey, StringCodec.INSTANCE)
                            .addAsync((double) gameCreation, matchId);
                    batch.getScoredSortedSet(zsetKey, StringCodec.INSTANCE)
                            .removeRangeByRankAsync(0, -(MATCH_IDS_TRIM_KEEP + 1));
                    batch.getKeys().expireAsync(zsetKey, MATCH_IDS_TTL.toSeconds(), TimeUnit.SECONDS);
                }
            }

            batch.execute();
            meterRegistry.counter("cache.match.write", "result", "success").increment();
        } catch (Exception e) {
            log.warn("cache.match.write failed matchId={} cause={}", matchId, e.getMessage());
            meterRegistry.counter("cache.match.write", "result", "failure").increment();
        }
    }
}
