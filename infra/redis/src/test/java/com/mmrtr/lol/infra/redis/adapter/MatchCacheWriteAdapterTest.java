package com.mmrtr.lol.infra.redis.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmrtr.lol.domain.match.readmodel.InfoDto;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.MetadataDto;
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
        adapter = new MatchCacheWriteAdapter(redissonClient, objectMapper, meterRegistry);
    }

    @Test
    @DisplayName("N 매치 SET + 요청자 ZSET 1개 ZADD/TRIM/EXPIRE 가 단일 batch 로 실행된다")
    void writeMatches_singleBatchForAllMatches() {
        MatchDto m1 = buildMatch("KR_1", 100L);
        MatchDto m2 = buildMatch("KR_2", 200L);
        MatchDto m3 = buildMatch("KR_3", 300L);

        adapter.writeMatches(List.of(m1, m2, m3), "p3");

        verify(batch, times(1)).execute();

        assertThat(bucketByKey).containsOnlyKeys("match:v1:KR_1", "match:v1:KR_2", "match:v1:KR_3");
        verify(bucketByKey.get("match:v1:KR_1")).setAsync(jsonContaining("KR_1"), eq(Duration.ofMinutes(3)));
        verify(bucketByKey.get("match:v1:KR_2")).setAsync(jsonContaining("KR_2"), eq(Duration.ofMinutes(3)));
        verify(bucketByKey.get("match:v1:KR_3")).setAsync(jsonContaining("KR_3"), eq(Duration.ofMinutes(3)));

        assertThat(zsetByKey).containsOnlyKeys("match:ids:v1:p3");
        RScoredSortedSetAsync<String> requesterZset = zsetByKey.get("match:ids:v1:p3");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Double>> membersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(requesterZset).addAllAsync(membersCaptor.capture());
        assertThat(membersCaptor.getValue())
                .containsOnly(entry("KR_1", 100.0), entry("KR_2", 200.0), entry("KR_3", 300.0));

        verify(requesterZset, times(1)).removeRangeByRankAsync(0, -21);
        verify(keys, times(1)).expireAsync(eq("match:ids:v1:p3"),
                eq(Duration.ofMinutes(3).toSeconds()), eq(TimeUnit.SECONDS));

        assertThat(meterRegistry.counter("cache.match.write", "result", "success").count())
                .isEqualTo(3.0);
    }

    @Test
    @DisplayName("요청자가 참가자가 아니어도 그 요청자의 ZSET 에 ZADD 한다")
    void writeMatches_requesterNotInParticipants_stillAdds() {
        adapter.writeMatches(List.of(buildMatch("KR_77", 1L)), "other-puuid");

        assertThat(zsetByKey).containsOnlyKeys("match:ids:v1:other-puuid");
    }

    @Test
    @DisplayName("RBatch.execute 실패 시 예외를 swallow + failure 카운터 증가")
    void writeMatches_swallowsException() {
        when(batch.execute()).thenThrow(new RuntimeException("redis down"));

        adapter.writeMatches(List.of(buildMatch("KR_1", 1L), buildMatch("KR_2", 2L)), "p0");

        assertThat(meterRegistry.counter("cache.match.write", "result", "failure").count())
                .isEqualTo(2.0);
        assertThat(meterRegistry.counter("cache.match.write", "result", "success").count())
                .isEqualTo(0.0);
    }

    @Test
    @DisplayName("리스트 내 invalid 매치는 skip 하고 나머지는 처리한다")
    void writeMatches_skipsInvalidAndProcessesRest() {
        MatchDto valid = buildMatch("KR_OK", 100L);
        MatchDto noMetadata = new MatchDto();
        noMetadata.setInfo(new InfoDto());
        MatchDto blankId = buildMatch("", 200L);

        adapter.writeMatches(java.util.Arrays.asList(valid, null, noMetadata, blankId), "p0");

        assertThat(bucketByKey).containsOnlyKeys("match:v1:KR_OK");
        assertThat(meterRegistry.counter("cache.match.write", "result", "success").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("매치 리스트가 비어있으면 아무 일도 일어나지 않는다")
    void writeMatches_skipsWhenEmpty() {
        adapter.writeMatches(Collections.emptyList(), "p0");
        adapter.writeMatches(null, "p0");

        verify(redissonClient, times(0)).createBatch();
    }

    @Test
    @DisplayName("requesterPuuid 가 null 또는 blank 면 warn 로그 + skipped 메트릭")
    void writeMatches_warnsOnBlankRequester() {
        adapter.writeMatches(List.of(buildMatch("KR_5", 1L)), null);
        adapter.writeMatches(List.of(buildMatch("KR_5", 1L)), "");
        adapter.writeMatches(List.of(buildMatch("KR_5", 1L)), "   ");

        verify(redissonClient, times(0)).createBatch();
        assertThat(meterRegistry.counter("cache.match.write", "result", "skipped").count())
                .isEqualTo(3.0);
    }

    @Test
    @DisplayName("리스트 안 매치가 모두 invalid 면 batch.execute 호출 안 함")
    void writeMatches_skipsWhenAllInvalid() {
        adapter.writeMatches(java.util.Arrays.asList((MatchDto) null, buildMatch("", 1L)), "p0");

        verify(batch, times(0)).execute();
    }

    private String jsonContaining(String matchId) {
        return org.mockito.ArgumentMatchers.argThat(json ->
                json != null && json.contains("\"matchId\":\"" + matchId + "\""));
    }

    private MatchDto buildMatch(String matchId, long gameCreation) {
        MatchDto match = new MatchDto();
        MetadataDto metadata = new MetadataDto();
        metadata.setMatchId(matchId);
        match.setMetadata(metadata);

        InfoDto info = new InfoDto();
        info.setGameCreation(gameCreation);
        match.setInfo(info);
        return match;
    }
}
