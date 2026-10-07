package com.mmrtr.lol.infra.redis.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mmrtr.lol.domain.champion.domain.ChampionRotation;
import com.mmrtr.lol.domain.gamedata.readmodel.VersionReadModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 버전·로테이션 캐시 어댑터와 공용 {@link JsonValueCache} — 키·TTL·JSON 왕복·장애 시 미스 처리. */
class GameDataCacheAdaptersTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final JsonValueCache cache = new JsonValueCache(redisTemplate, new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));

    private final VersionCacheRedisAdapter versionAdapter = new VersionCacheRedisAdapter(cache);
    private final ChampionRotationCacheRedisAdapter rotationAdapter = new ChampionRotationCacheRedisAdapter(cache);

    GameDataCacheAdaptersTest() {
        given(redisTemplate.opsForValue()).willReturn(values);
    }

    @DisplayName("최신 버전은 gamedata:version:latest 에 10분, JSON 왕복")
    @Test
    void latestVersionRoundTrip() {
        VersionReadModel version = new VersionReadModel(3L, "16.16", "16.16.1", LocalDateTime.of(2026, 8, 13, 10, 0));
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);

        versionAdapter.saveLatestVersion(version);

        verify(values).set(eq("gamedata:version:latest"), json.capture(), eq(Duration.ofMinutes(10)));
        given(values.get("gamedata:version:latest")).willReturn(json.getValue());
        assertThat(versionAdapter.findLatestVersion()).contains(version);
    }

    @DisplayName("로테이션: 정상은 1시간, 빈 값은 60초. 키는 소문자 platform")
    @Test
    void rotationTtl() {
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);

        rotationAdapter.save("KR", new ChampionRotation(List.of(1, 2), List.of(101), 10));
        rotationAdapter.save("na", new ChampionRotation(List.of(), List.of(), 0));

        verify(values).set(eq("gamedata:champion-rotation:kr"), json.capture(), eq(Duration.ofHours(1)));
        verify(values).set(eq("gamedata:champion-rotation:na"), anyString(), eq(Duration.ofSeconds(60)));

        given(values.get("gamedata:champion-rotation:kr")).willReturn(json.getValue());
        ChampionRotation cached = rotationAdapter.find("kr").orElseThrow();
        assertThat(cached.getFreeChampionIds()).containsExactly(1, 2);
        assertThat(cached.getMaxNewPlayerLevel()).isEqualTo(10);
    }

    @DisplayName("Redis 장애·깨진 값은 캐시 미스, 저장 실패는 예외를 던지지 않는다")
    @Test
    void failureIsMiss() {
        given(values.get("gamedata:version:latest")).willThrow(new RedisConnectionFailureException("down"));
        given(values.get("gamedata:champion-rotation:kr")).willReturn("{not json");
        willThrow(new RedisConnectionFailureException("down"))
                .given(values).set(anyString(), anyString(), any(Duration.class));

        assertThat(versionAdapter.findLatestVersion()).isEmpty();
        assertThat(rotationAdapter.find("kr")).isEmpty();
        rotationAdapter.save("kr", new ChampionRotation(List.of(1), List.of(), 10));
    }
}
