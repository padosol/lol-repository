package com.mmrtr.lol.infra.redis.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmrtr.lol.domain.match.readmodel.InfoDto;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.MetadataDto;
import com.mmrtr.lol.domain.match.readmodel.ParticipantDto;
import com.mmrtr.lol.infra.redis.adapter.view.MatchCacheViewMapper;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchCacheWriteAdapterTest {

    private RedissonClient redissonClient;
    private RBatch batch;
    private Map<String, RBucketAsync<String>> bucketByKey;
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
        bucketByKey = new HashMap<>();
        keys = mock(RKeysAsync.class);
        zsetByKey = new HashMap<>();

        when(redissonClient.createBatch()).thenReturn(batch);
        when(batch.<String>getBucket(anyString(), any(Codec.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return bucketByKey.computeIfAbsent(key, k -> mock(RBucketAsync.class));
        });
        when(batch.getKeys()).thenReturn(keys);
        when(batch.<String>getScoredSortedSet(anyString(), any(Codec.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return zsetByKey.computeIfAbsent(key, k -> mock(RScoredSortedSetAsync.class));
        });

        meterRegistry = new SimpleMeterRegistry();
        objectMapper = new ObjectMapper();
        adapter = new MatchCacheWriteAdapter(
                redissonClient, objectMapper, meterRegistry, new MatchCacheViewMapper());
    }

    @Test
    @DisplayName("N 매치는 GameReadModel 형태 JSON 으로 SET + 요청자 ZSET 1개에 ZADD/TRIM/EXPIRE 단일 batch")
    void writeMatches_serializesViewAndSingleBatch() {
        MatchDto m1 = buildMatch("KR_1", 100L, "Ahri");
        MatchDto m2 = buildMatch("KR_2", 200L, "Caitlyn");

        adapter.writeMatches(List.of(m1, m2), null, "p3");

        verify(batch, times(1)).execute();
        assertThat(bucketByKey).containsOnlyKeys("match:v1:KR_1", "match:v1:KR_2");

        // GameReadModel 형태인지 확인 (gameInfoData.matchId, participantData[].championName)
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(bucketByKey.get("match:v1:KR_1"))
                .setAsync(jsonCaptor.capture(), eq(Duration.ofMinutes(3)));
        String json = jsonCaptor.getValue();
        assertThat(json).contains("\"gameInfoData\"");
        assertThat(json).contains("\"matchId\":\"KR_1\"");
        assertThat(json).contains("\"participantData\"");
        assertThat(json).contains("\"championName\":\"Ahri\"");
        assertThat(json).contains("\"teamInfoData\"");

        assertThat(zsetByKey).containsOnlyKeys("match:ids:v1:p3");
        RScoredSortedSetAsync<String> zset = zsetByKey.get("match:ids:v1:p3");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Double>> membersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(zset).addAllAsync(membersCaptor.capture());
        assertThat(membersCaptor.getValue()).containsOnly(entry("KR_1", 100.0), entry("KR_2", 200.0));
        verify(zset, times(1)).removeRangeByRankAsync(0, -21);
        verify(keys, times(1)).expireAsync(eq("match:ids:v1:p3"),
                eq(Duration.ofMinutes(3).toSeconds()), eq(TimeUnit.SECONDS));

        assertThat(meterRegistry.counter("cache.match.write", "result", "success").count())
                .isEqualTo(2.0);
    }

    @Test
    @DisplayName("RBatch.execute 실패 시 swallow + failure 카운터")
    void writeMatches_swallowsException() {
        when(batch.execute()).thenThrow(new RuntimeException("redis down"));

        adapter.writeMatches(List.of(buildMatch("KR_1", 1L, "Ahri")), null, "p0");

        assertThat(meterRegistry.counter("cache.match.write", "result", "failure").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("invalid 매치는 skip 하고 나머지 처리")
    void writeMatches_skipsInvalid() {
        MatchDto valid = buildMatch("KR_OK", 100L, "Ahri");
        MatchDto noMetadata = new MatchDto();
        noMetadata.setInfo(new InfoDto());

        adapter.writeMatches(Arrays.asList(valid, null, noMetadata), null, "p0");

        assertThat(bucketByKey).containsOnlyKeys("match:v1:KR_OK");
        assertThat(meterRegistry.counter("cache.match.write", "result", "success").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("매치 리스트가 비어있으면 아무 일도 일어나지 않는다")
    void writeMatches_skipsWhenEmpty() {
        adapter.writeMatches(Collections.emptyList(), null, "p0");
        adapter.writeMatches(null, null, "p0");

        verify(redissonClient, times(0)).createBatch();
    }

    @Test
    @DisplayName("requesterPuuid blank 면 warn + skipped 메트릭")
    void writeMatches_warnsOnBlankRequester() {
        adapter.writeMatches(List.of(buildMatch("KR_5", 1L, "Ahri")), null, null);
        adapter.writeMatches(List.of(buildMatch("KR_5", 1L, "Ahri")), null, "  ");

        verify(redissonClient, times(0)).createBatch();
        assertThat(meterRegistry.counter("cache.match.write", "result", "skipped").count())
                .isEqualTo(2.0);
    }

    private MatchDto buildMatch(String matchId, long gameCreation, String championName) {
        MatchDto match = new MatchDto();
        MetadataDto metadata = new MetadataDto();
        metadata.setMatchId(matchId);
        match.setMetadata(metadata);

        InfoDto info = new InfoDto();
        info.setGameCreation(gameCreation);
        List<ParticipantDto> participants = new ArrayList<>();
        ParticipantDto p = new ParticipantDto();
        p.setPuuid("p0");
        p.setChampionName(championName);
        p.setParticipantId(1);
        participants.add(p);
        info.setParticipants(participants);
        match.setInfo(info);
        return match;
    }
}
