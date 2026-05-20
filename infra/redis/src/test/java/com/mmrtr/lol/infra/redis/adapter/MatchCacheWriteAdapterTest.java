package com.mmrtr.lol.infra.redis.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmrtr.lol.domain.match.readmodel.InfoDto;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.MetadataDto;
import com.mmrtr.lol.domain.match.readmodel.ParticipantDto;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RBatch;
import org.redisson.api.RBucketAsync;
import org.redisson.api.RKeysAsync;
import org.redisson.api.RScoredSortedSetAsync;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchCacheWriteAdapterTest {

    private RedissonClient redissonClient;
    private RBatch batch;
    private RBucketAsync<String> bucket;
    private RKeysAsync keys;
    private Map<String, RScoredSortedSetAsync<String>> zsetByKey;
    private MeterRegistry meterRegistry;
    private ObjectMapper objectMapper;
    private MatchCacheWriteAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        batch = mock(RBatch.class);
        bucket = mock(RBucketAsync.class);
        keys = mock(RKeysAsync.class);
        zsetByKey = new HashMap<>();

        when(redissonClient.createBatch()).thenReturn(batch);
        when(batch.<String>getBucket(anyString(), any(Codec.class))).thenReturn(bucket);
        when(batch.getKeys()).thenReturn(keys);
        when(batch.<String>getScoredSortedSet(anyString(), any(Codec.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return zsetByKey.computeIfAbsent(key, k -> mock(RScoredSortedSetAsync.class));
        });

        meterRegistry = new SimpleMeterRegistry();
        objectMapper = new ObjectMapper();
        adapter = new MatchCacheWriteAdapter(redissonClient, objectMapper, meterRegistry);
    }

    @Test
    @DisplayName("매치 SET + 참가자 10명 ZADD/TRIM/EXPIRE 가 모두 한 batch 로 실행된다")
    void writeMatch_writesBucketAndAllParticipantZSets() {
        MatchDto match = buildMatch("KR_42", 1700000000000L, List.of(
                "p0", "p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9"
        ));

        adapter.writeMatch(match);

        verify(batch).execute();

        // 단건 SET 확인
        verify(batch).getBucket(eq("match:v1:KR_42"), any(Codec.class));
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(bucket).setAsync(jsonCaptor.capture(), eq(Duration.ofHours(1)));
        assertThat(jsonCaptor.getValue()).contains("\"matchId\":\"KR_42\"");

        // 참가자 10명 각각에 대해 ZADD / TRIM / EXPIRE
        for (int i = 0; i < 10; i++) {
            String zsetKey = "match:ids:v1:p" + i;
            RScoredSortedSetAsync<String> zset = zsetByKey.get(zsetKey);
            assertThat(zset).as("zset for puuid p%d", i).isNotNull();
            verify(zset).addAsync(1700000000000.0, "KR_42");
            verify(zset).removeRangeByRankAsync(0, -21);
            verify(keys).expireAsync(eq(zsetKey), eq(Duration.ofHours(24).toSeconds()), eq(TimeUnit.SECONDS));
        }

        assertThat(meterRegistry.counter("cache.match.write", "result", "success").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter("cache.match.write", "result", "failure").count())
                .isEqualTo(0.0);
    }

    @Test
    @DisplayName("RBatch.execute 가 실패해도 예외를 swallow 하고 failure 카운터를 증가시킨다")
    void writeMatch_swallowsException() {
        when(batch.execute()).thenThrow(new RuntimeException("redis down"));

        MatchDto match = buildMatch("KR_1", 1L, List.of("p0"));

        adapter.writeMatch(match); // 예외 전파 X

        assertThat(meterRegistry.counter("cache.match.write", "result", "failure").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter("cache.match.write", "result", "success").count())
                .isEqualTo(0.0);
    }

    @Test
    @DisplayName("metadata 가 null 이면 아무 일도 일어나지 않는다")
    void writeMatch_skipsWhenMetadataMissing() {
        MatchDto match = new MatchDto();
        match.setInfo(new InfoDto());

        adapter.writeMatch(match);

        verify(redissonClient, times(0)).createBatch();
    }

    @Test
    @DisplayName("matchId 가 비어있으면 아무 일도 일어나지 않는다")
    void writeMatch_skipsWhenMatchIdBlank() {
        MatchDto match = buildMatch("", 1L, List.of("p0"));

        adapter.writeMatch(match);

        verify(redissonClient, times(0)).createBatch();
    }

    @Test
    @DisplayName("null 매치는 NPE 없이 무시된다")
    void writeMatch_nullSafe() {
        adapter.writeMatch(null);

        verify(redissonClient, times(0)).createBatch();
    }

    @Test
    @DisplayName("blank puuid 는 ZADD 대상에서 제외된다")
    void writeMatch_skipsBlankPuuid() {
        MatchDto match = buildMatch("KR_99", 100L, new ArrayList<>(List.of("p0", "", "p1")));

        adapter.writeMatch(match);

        assertThat(zsetByKey).containsOnlyKeys("match:ids:v1:p0", "match:ids:v1:p1");
        verify(batch, atLeastOnce()).execute();
    }

    private MatchDto buildMatch(String matchId, long gameCreation, List<String> puuids) {
        MatchDto match = new MatchDto();
        MetadataDto metadata = new MetadataDto();
        metadata.setMatchId(matchId);
        match.setMetadata(metadata);

        InfoDto info = new InfoDto();
        info.setGameCreation(gameCreation);
        List<ParticipantDto> participants = new ArrayList<>();
        for (String puuid : puuids) {
            ParticipantDto p = new ParticipantDto();
            p.setPuuid(puuid);
            participants.add(p);
        }
        info.setParticipants(participants);
        match.setInfo(info);
        return match;
    }
}
