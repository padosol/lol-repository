package com.mmrtr.lol.infra.redis.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmrtr.lol.domain.spectator.readmodel.ActiveGameReadModel;
import com.mmrtr.lol.domain.spectator.readmodel.ActiveGameReadModel.Banned;
import com.mmrtr.lol.domain.spectator.readmodel.ActiveGameReadModel.Participant;
import com.mmrtr.lol.domain.spectator.readmodel.ActiveGameReadModel.Perks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 관전 캐시 — 참여자 전원 키, TTL, JSON 왕복(isBot 키), 장애 시 "게임 없음 캐시 없음". */
class ActiveGameCacheRedisAdapterTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ActiveGameCacheRedisAdapter adapter =
            new ActiveGameCacheRedisAdapter(new JsonValueCache(redisTemplate, new ObjectMapper()));

    ActiveGameCacheRedisAdapterTest() {
        given(redisTemplate.opsForValue()).willReturn(values);
    }

    @DisplayName("게임은 참여자 전원의 puuid 키에 30분 저장하고, 같은 모양으로 읽힌다")
    @Test
    void saveGame_participants_storesUnderEveryPuuid() {
        // given
        ActiveGameReadModel game = game();
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);

        // when
        adapter.saveGame("KR", game);

        // then
        verify(values).set(eq("spectator:v1:active-game:KR:p1"), json.capture(), eq(Duration.ofMinutes(30)));
        verify(values).set(eq("spectator:v1:active-game:KR:p2"), anyString(), eq(Duration.ofMinutes(30)));
        assertThat(json.getValue()).contains("\"isBot\":true");
        given(values.get("spectator:v1:active-game:KR:p2")).willReturn(json.getValue());
        assertThat(adapter.findGame("KR", "p2")).contains(game);
    }

    @DisplayName("무효화는 참여자 전원의 키를 지운다")
    @Test
    void evictGame_participants_deletesEveryPuuidKey() {
        // when
        adapter.evictGame("KR", game());

        // then
        verify(redisTemplate).delete(List.of("spectator:v1:active-game:KR:p1", "spectator:v1:active-game:KR:p2"));
    }

    @DisplayName("게임 없음은 30초, Redis 장애면 캐시가 없는 것으로 본다")
    @Test
    void notInGame_ttlAndFailure_isShortAndFailOpen() {
        // when
        adapter.saveNotInGame("KR", "p1");
        given(redisTemplate.hasKey("spectator:v1:not-in-game:KR:p1")).willReturn(true);
        given(redisTemplate.hasKey("spectator:v1:not-in-game:KR:p2"))
                .willThrow(new RedisConnectionFailureException("down"));

        // then
        verify(values).set(eq("spectator:v1:not-in-game:KR:p1"), anyString(), eq(Duration.ofSeconds(30)));
        assertThat(adapter.isNotInGame("KR", "p1")).isTrue();
        assertThat(adapter.isNotInGame("KR", "p2")).isFalse();
    }

    private ActiveGameReadModel game() {
        Perks perks = new Perks(8100L, 8300L, List.of(8112L, 8126L));
        return new ActiveGameReadModel(1L, "MATCHED_GAME", "CLASSIC", 11L, 420L, 1_706_400_000_000L, 600L, "KR",
                "key-123",
                List.of(new Participant("a#KR1", "p1", 1L, 100L, 4L, 14L, 1L, false, perks),
                        new Participant("b#KR1", "p2", 2L, 200L, 7L, 11L, 2L, true, perks)),
                List.of(new Banned(157L, 100L, 1)));
    }
}
