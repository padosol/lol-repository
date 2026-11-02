package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerMessage;
import com.mmrtr.lol.infra.rabbitmq.service.SummonerRenewalService;
import com.mmrtr.lol.infra.redis.service.RedisLockHandler;
import com.mmrtr.lol.infra.riot.exception.RiotBudgetExceededException;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotCallScope;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SummonerRenewalListenerTest {

    private final List<RiotCallPriority> seen = new ArrayList<>();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private RedisLockHandler lock;
    private SummonerRenewalService service;
    private SummonerRenewalListener listener;

    @BeforeEach
    void setUp() {
        lock = mock(RedisLockHandler.class);
        when(lock.acquireLock(anyString(), any())).thenReturn(true);
        service = mock(SummonerRenewalService.class);
        doAnswer(inv -> {
            seen.add(RiotCallScope.current());
            return null;
        }).when(service).renewSummoner(anyString(), any(Platform.class));
        listener = new SummonerRenewalListener(lock, service, registry);
    }

    private double count(String result, String reason, String priority) {
        var counter = registry.find("lol.renewal.completed")
                .tags("result", result, "reason", reason, "priority", priority).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    @DisplayName("사용자 갱신 메시지(background 없음)는 USER 우선순위로 호출된다")
    void receiveSummonerMessageV2_사용자갱신_USER우선순위로호출() {
        // given
        SummonerMessage message = new SummonerMessage("KR", "puuid", 1L);

        // when
        listener.receiveSummonerMessageV2(message);

        // then
        assertThat(seen).containsExactly(RiotCallPriority.USER);
        assertThat(RiotCallScope.current()).isEqualTo(RiotCallPriority.USER);
    }

    @Test
    @DisplayName("background 메시지(티어 수집)는 BACKGROUND 스코프에서 호출된다")
    void receiveSummonerMessageV2_background메시지_BACKGROUND스코프로호출() {
        // given
        SummonerMessage message = new SummonerMessage("KR", "puuid", 1L, true);

        // when
        listener.receiveSummonerMessageV2(message);

        // then
        assertThat(seen).containsExactly(RiotCallPriority.BACKGROUND);
        assertThat(RiotCallScope.current()).isEqualTo(RiotCallPriority.USER);
    }

    @Test
    @DisplayName("성공하면 success/none 으로 센다")
    void receiveSummonerMessageV2_성공_success와none으로센다() {
        // given
        SummonerMessage message = new SummonerMessage("KR", "puuid", 1L);

        // when
        listener.receiveSummonerMessageV2(message);

        // then
        assertThat(count("success", "none", "user")).isEqualTo(1);
    }

    @Test
    @DisplayName("예산 초과로 실패하면 failed/budget_exceeded 로 세고 잠금은 푼다")
    void receiveSummonerMessageV2_예산초과_failed와budget_exceeded로세고잠금해제() {
        // given
        doThrow(new CompletionException(new RiotBudgetExceededException("asia", RiotCallPriority.USER)))
                .when(service).renewSummoner(anyString(), any(Platform.class));

        // when
        listener.receiveSummonerMessageV2(new SummonerMessage("KR", "puuid", 1L));

        // then
        assertThat(count("failed", "budget_exceeded", "user")).isEqualTo(1);
        verify(lock).releaseLock("puuid");
    }

    @Test
    @DisplayName("Riot 호출이 HTTP 읽기 시간 초과로 실패하면 failed/timeout 으로 센다")
    void receiveSummonerMessageV2_HTTP읽기시간초과_failed와timeout으로센다() {
        // given
        doThrow(new ResourceAccessException("I/O error on GET request", new HttpTimeoutException("request timed out")))
                .when(service).renewSummoner(anyString(), any(Platform.class));

        // when
        listener.receiveSummonerMessageV2(new SummonerMessage("KR", "puuid", 1L));

        // then
        assertThat(count("failed", "timeout", "user")).isEqualTo(1);
        assertThat(count("failed", "other", "user")).isZero();
    }

    @Test
    @DisplayName("잠금 중이면 skipped/lock_busy 로 센다")
    void receiveSummonerMessageV2_잠금중_skipped와lock_busy로센다() {
        // given
        when(lock.acquireLock(anyString(), any())).thenReturn(false);

        // when
        listener.receiveSummonerMessageV2(new SummonerMessage("KR", "puuid", 1L));

        // then
        assertThat(count("skipped", "lock_busy", "user")).isEqualTo(1);
    }

    @Test
    @DisplayName("background 메시지는 priority=background 로 센다")
    void receiveSummonerMessageV2_background메시지_priority를background로센다() {
        // given
        SummonerMessage message = new SummonerMessage("KR", "puuid", 1L, true);

        // when
        listener.receiveSummonerMessageV2(message);

        // then
        assertThat(count("success", "none", "background")).isEqualTo(1);
        assertThat(count("success", "none", "user")).isZero();
    }

    @Test
    @DisplayName("유효하지 않은 platform 은 failed/other 로 센다")
    void receiveSummonerMessageV2_잘못된platform_failed와other로센다() {
        // given
        SummonerMessage message = new SummonerMessage("XX", "puuid", 1L);

        // when
        listener.receiveSummonerMessageV2(message);

        // then
        assertThat(count("failed", "other", "user")).isEqualTo(1);
    }

    @Test
    @DisplayName("실패해도 예외를 밖으로 던지지 않는다")
    void receiveSummonerMessageV2_실패_예외를던지지않고failed로센다() {
        // given
        doThrow(new IllegalStateException("boom"))
                .when(service).renewSummoner(anyString(), any(Platform.class));
        SummonerMessage message = new SummonerMessage("KR", "puuid", 1L);

        // when // then
        assertThatCode(() -> listener.receiveSummonerMessageV2(message))
                .doesNotThrowAnyException();
        assertThat(count("failed", "other", "user")).isEqualTo(1);
    }
}
