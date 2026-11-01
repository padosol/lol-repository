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
    void userRenewal_runsUnderUser() {
        listener.receiveSummonerMessageV2(new SummonerMessage("KR", "puuid", 1L));

        assertThat(seen).containsExactly(RiotCallPriority.USER);
        assertThat(RiotCallScope.current()).isEqualTo(RiotCallPriority.USER);
    }

    @Test
    @DisplayName("background 메시지(티어 수집)는 BACKGROUND 스코프에서 호출된다")
    void backgroundRenewal_runsUnderBackground() {
        listener.receiveSummonerMessageV2(new SummonerMessage("KR", "puuid", 1L, true));

        assertThat(seen).containsExactly(RiotCallPriority.BACKGROUND);
        assertThat(RiotCallScope.current()).isEqualTo(RiotCallPriority.USER);
    }

    @Test
    @DisplayName("성공하면 success/none 으로 센다")
    void renew_success_countsSuccessNone() {
        listener.receiveSummonerMessageV2(new SummonerMessage("KR", "puuid", 1L));

        assertThat(count("success", "none", "user")).isEqualTo(1);
    }

    @Test
    @DisplayName("예산 초과로 실패하면 failed/budget_exceeded 로 세고 잠금은 푼다")
    void renew_budgetExceeded_countsFailed() {
        doThrow(new CompletionException(new RiotBudgetExceededException("asia", RiotCallPriority.USER)))
                .when(service).renewSummoner(anyString(), any(Platform.class));

        listener.receiveSummonerMessageV2(new SummonerMessage("KR", "puuid", 1L));

        assertThat(count("failed", "budget_exceeded", "user")).isEqualTo(1);
        verify(lock).releaseLock("puuid");
    }

    @Test
    @DisplayName("잠금 중이면 skipped/lock_busy 로 센다")
    void renew_lockBusy_countsSkipped() {
        when(lock.acquireLock(anyString(), any())).thenReturn(false);

        listener.receiveSummonerMessageV2(new SummonerMessage("KR", "puuid", 1L));

        assertThat(count("skipped", "lock_busy", "user")).isEqualTo(1);
    }

    @Test
    @DisplayName("background 메시지는 priority=background 로 센다")
    void renew_background_countsBackgroundPriority() {
        listener.receiveSummonerMessageV2(new SummonerMessage("KR", "puuid", 1L, true));

        assertThat(count("success", "none", "background")).isEqualTo(1);
        assertThat(count("success", "none", "user")).isZero();
    }

    @Test
    @DisplayName("유효하지 않은 platform 은 failed/other 로 센다")
    void renew_invalidPlatform_countsFailedOther() {
        listener.receiveSummonerMessageV2(new SummonerMessage("XX", "puuid", 1L));

        assertThat(count("failed", "other", "user")).isEqualTo(1);
    }

    @Test
    @DisplayName("실패해도 예외를 밖으로 던지지 않는다")
    void renew_failure_doesNotThrow() {
        doThrow(new IllegalStateException("boom"))
                .when(service).renewSummoner(anyString(), any(Platform.class));

        assertThatCode(() -> listener.receiveSummonerMessageV2(new SummonerMessage("KR", "puuid", 1L)))
                .doesNotThrowAnyException();
        assertThat(count("failed", "other", "user")).isEqualTo(1);
    }
}
