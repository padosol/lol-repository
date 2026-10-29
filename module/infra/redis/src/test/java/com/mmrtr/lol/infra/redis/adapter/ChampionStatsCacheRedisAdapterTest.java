package com.mmrtr.lol.infra.redis.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionAverageStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionBootBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionItemBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionMatchupReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionPositionStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionRateReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionRuneBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionSkillBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionSpellStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionStartItemBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.PositionChampionStatsReadModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RKeys;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.KeysScanOptions;
import org.redisson.client.RedisException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 챔피언 통계 캐시 — 키·TTL·JSON 왕복·장애 시 미스, 그리고 single-flight 락 (lol-server {@code ChampionStatsCacheAdapterTest} 대응). */
class ChampionStatsCacheRedisAdapterTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final ChampionStatsCacheRedisAdapter adapter = new ChampionStatsCacheRedisAdapter(
            new JsonValueCache(redisTemplate, new ObjectMapper()), redissonClient);

    ChampionStatsCacheRedisAdapterTest() {
        given(redisTemplate.opsForValue()).willReturn(values);
    }

    @DisplayName("상세 통계는 championstats:v1:detail:{platformId}:{championId}:{patch}:{tier} 에 6시간, JSON 왕복")
    @Test
    void championStatsRoundTrip() {
        // given
        ChampionStatsReadModel stats = new ChampionStatsReadModel("MASTER+", List.of(new ChampionPositionStatsReadModel(
                "TOP", 0.55, 0.075, 0.04, "S", 1500,
                new ChampionAverageStatsReadModel("TOP", 6.2, 5.4, 7.8, 2.59, 380.5, 65.3, 12.1),
                List.of(ChampionMatchupReadModel.top(86, 200, 0.55, 0.4)),
                List.of(new ChampionRuneBuildReadModel(8000, 8400, 8010, 9111, 9104, 8299, 8446, 8451, 250, 0.56, 0.5)
                        .withConfidence(1500, 0.49)),
                List.of(new ChampionSpellStatsReadModel(4, 14, 800, 0.52, 0.8).withConfidence(1500, 0.48)),
                List.of(new ChampionSkillBuildReadModel("[1,3,2,1,1,4]", 400, 0.55, 0.4).withConfidence(1500, 0.5)),
                List.of(new ChampionStartItemBuildReadModel(List.of(1054, 2003), 600, 0.51, 0.6)),
                List.of(new ChampionBootBuildReadModel(3047, 700, 0.53, 0.7)),
                List.of(new ChampionItemBuildReadModel(List.of(3078, 3053, 3065), 300, 0.5667, 0.3)))));
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);

        // when
        adapter.saveChampionStats(266, "14.24", "KR", "MASTER+", stats);

        // then
        String key = "championstats:v1:detail:KR:266:14.24:MASTER+";
        verify(values).set(eq(key), json.capture(), eq(Duration.ofHours(6)));
        given(values.get(key)).willReturn(json.getValue());
        assertThat(adapter.findChampionStats(266, "14.24", "KR", "MASTER+")).contains(stats);
    }

    @DisplayName("포지션별 통계는 championstats:v1:positions:{platformId}:{patch}:{tier} — totalGames 는 JSON 에서 빠져 0 으로 읽힌다")
    @Test
    void positionsRoundTrip() {
        // given
        List<PositionChampionStatsReadModel> positions = List.of(new PositionChampionStatsReadModel("TOP", List.of(
                new ChampionRateReadModel(266, 0.52, 0.08, 0.05, 1500, "S+"))));
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);

        // when
        adapter.saveChampionStatsByPosition("16.1", "KR", "EMERALD", positions);

        // then
        String key = "championstats:v1:positions:KR:16.1:EMERALD";
        verify(values).set(eq(key), json.capture(), eq(Duration.ofHours(6)));
        assertThat(json.getValue()).doesNotContain("totalGames");
        given(values.get(key)).willReturn(json.getValue());
        assertThat(adapter.findChampionStatsByPosition("16.1", "KR", "EMERALD")).contains(List.of(
                new PositionChampionStatsReadModel("TOP", List.of(new ChampionRateReadModel(266, 0.52, 0.08, 0.05, 0, "S+")))));
    }

    @DisplayName("빈 포지션 목록도 캐시 히트다 (미스와 구분)")
    @Test
    void emptyPositionsIsHit() {
        // given
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        adapter.saveChampionStatsByPosition("16.1", "KR", "EMERALD", List.of());
        verify(values).set(anyString(), json.capture(), eq(Duration.ofHours(6)));
        given(values.get("championstats:v1:positions:KR:16.1:EMERALD")).willReturn(json.getValue());

        // when & then
        assertThat(adapter.findChampionStatsByPosition("16.1", "KR", "EMERALD")).contains(List.of());
    }

    @DisplayName("캐시 미스·Redis 장애는 empty")
    @Test
    void missOrRedisFailure_returnsEmpty() {
        // given
        given(values.get("championstats:v1:positions:KR:16.1:EMERALD"))
                .willThrow(new RedisConnectionFailureException("down"));

        // when & then
        assertThat(adapter.findChampionStats(266, "16.1", "KR", "EMERALD")).isEmpty();
        assertThat(adapter.findChampionStatsByPosition("16.1", "KR", "EMERALD")).isEmpty();
    }

    @DisplayName("tryLockDetail 은 상세 락 키로 대기 3초·lease 30초 RLock 을 잡는다")
    @Test
    void tryLockDetail_usesDetailKeyAndTimings() throws InterruptedException {
        // given
        given(redissonClient.getLock("championstats:lock:detail:KR:266:14.24:EMERALD")).willReturn(lock);
        given(lock.tryLock(3L, 30L, TimeUnit.SECONDS)).willReturn(true);

        // when & then
        assertThat(adapter.tryLockDetail(266, "14.24", "KR", "EMERALD")).isTrue();
    }

    @DisplayName("tryLockByPosition 은 포지션 락 키를 쓴다")
    @Test
    void tryLockByPosition_usesPositionsKey() throws InterruptedException {
        // given
        given(redissonClient.getLock("championstats:lock:positions:KR:14.24:MASTER+")).willReturn(lock);
        given(lock.tryLock(3L, 30L, TimeUnit.SECONDS)).willReturn(false);

        // when & then
        assertThat(adapter.tryLockByPosition("14.24", "KR", "MASTER+")).isFalse();
    }

    @DisplayName("락 대기 중 인터럽트는 false 를 돌려주고 인터럽트 플래그를 복원한다")
    @Test
    void tryLock_interrupted_returnsFalseAndRestoresFlag() throws InterruptedException {
        // given
        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(3L, 30L, TimeUnit.SECONDS)).willThrow(new InterruptedException("test"));

        // when
        boolean acquired = adapter.tryLockDetail(266, "14.24", "KR", "EMERALD");

        // then
        assertThat(acquired).isFalse();
        assertThat(Thread.interrupted()).isTrue();
    }

    @DisplayName("unlock 은 현재 스레드가 잡은 락만 푼다")
    @Test
    void unlock_releasesOnlyWhenHeldByCurrentThread() {
        // given
        RLock held = mock(RLock.class);
        RLock notHeld = mock(RLock.class);
        given(redissonClient.getLock("championstats:lock:detail:KR:266:14.24:EMERALD")).willReturn(held);
        given(redissonClient.getLock("championstats:lock:positions:KR:14.24:EMERALD")).willReturn(notHeld);
        given(held.isHeldByCurrentThread()).willReturn(true);
        given(notHeld.isHeldByCurrentThread()).willReturn(false);

        // when
        adapter.unlockDetail(266, "14.24", "KR", "EMERALD");
        adapter.unlockByPosition("14.24", "KR", "EMERALD");

        // then
        verify(held).unlock();
        verify(notHeld, never()).unlock();
    }

    @DisplayName("자기 상세·포지션 키와 레거시 패턴 키를 SCAN 으로 찾아 UNLINK 한다")
    @Test
    void evictAll_unlinksOwnAndLegacyKeys() {
        // given
        ReflectionTestUtils.setField(adapter, "legacyEvictPatterns", List.of("champion-stats:v[0-9]*"));
        RKeys rKeys = mock(RKeys.class);
        given(redissonClient.getKeys()).willReturn(rKeys);
        given(rKeys.getKeys(any(KeysScanOptions.class))).willReturn(List.of("k1"));
        given(rKeys.unlink(any(String[].class))).willReturn(1L);

        // when
        long evicted = adapter.evictAll();

        // then
        assertThat(evicted).isEqualTo(3);
        verify(rKeys, times(3)).getKeys(any(KeysScanOptions.class));
        verify(rKeys, times(3)).unlink(any(String[].class));
    }

    @DisplayName("레거시 패턴이 null 이면 자기 키 패턴만 지운다")
    @Test
    void evictAll_nullLegacyPatterns_onlyOwnKeys() {
        // given
        ReflectionTestUtils.setField(adapter, "legacyEvictPatterns", null);
        RKeys rKeys = mock(RKeys.class);
        given(redissonClient.getKeys()).willReturn(rKeys);
        given(rKeys.getKeys(any(KeysScanOptions.class))).willReturn(List.of("k1"));
        given(rKeys.unlink(any(String[].class))).willReturn(1L);

        // when
        long evicted = adapter.evictAll();

        // then
        assertThat(evicted).isEqualTo(2);
        verify(rKeys, times(2)).getKeys(any(KeysScanOptions.class));
    }

    @DisplayName("Redis 장애면 예외 없이 0")
    @Test
    void evictAll_redisFailure_returnsZero() {
        // given
        RKeys rKeys = mock(RKeys.class);
        given(redissonClient.getKeys()).willReturn(rKeys);
        given(rKeys.getKeys(any(KeysScanOptions.class))).willThrow(new RedisException("down"));

        // when
        long evicted = adapter.evictAll();

        // then
        assertThat(evicted).isZero();
    }
}
