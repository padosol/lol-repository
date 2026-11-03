package com.mmrtr.lol.infra.rabbitmq.renewal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;
import com.mmrtr.lol.domain.summoner.application.port.SummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.domain.RenewalRecord;
import com.mmrtr.lol.domain.summoner.domain.RenewalStatus;
import com.mmrtr.lol.domain.summoner.domain.RenewalStep;
import com.mmrtr.lol.domain.summoner.domain.RenewalTransition;
import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.RenewalStepMessage;
import com.mmrtr.lol.infra.riot.dto.account.AccountDto;
import com.mmrtr.lol.infra.riot.exception.RiotBudgetExceededException;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotCallScope;
import com.mmrtr.lol.support.riot.RiotRetryScope;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RenewalAccountStepTest {

    private final RenewalRecordPort recordPort = mock(RenewalRecordPort.class);
    private final SummonerRepositoryPort summonerRepositoryPort = mock(SummonerRepositoryPort.class);
    private final RiotApiService riotApiService = mock(RiotApiService.class);
    private final RenewalStepPublisher stepPublisher = mock(RenewalStepPublisher.class);
    private final RenewalFinisher finisher = mock(RenewalFinisher.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final Executor executor = Runnable::run;
    private final RenewalAccountStep step = new RenewalAccountStep(
            recordPort, summonerRepositoryPort, riotApiService, stepPublisher, finisher, meterRegistry, executor);

    @BeforeEach
    void setUp() {
        givenRecord("r1");
        AccountDto account = new AccountDto();
        account.setPuuid("p1");
        account.setGameName("Hide on bush");
        account.setTagLine("KR1");
        given(riotApiService.getAccountByPuuid(eq("p1"), eq(Platform.KR), any()))
                .willReturn(CompletableFuture.completedFuture(account));
        given(recordPort.markSteps("p1", "r1", EnumSet.of(RenewalStep.ACCOUNT))).willReturn(RenewalTransition.NONE);
    }

    private void givenRecord(String renewalId) {
        given(recordPort.find("p1")).willReturn(Optional.of(
                new RenewalRecord(renewalId, RenewalStatus.PROGRESS, null, 0, false, 1_000L, 6_000L, null, "KR")));
    }

    private RenewalStepMessage msg(String renewalId) {
        return new RenewalStepMessage(renewalId, "p1", "KR", 0, 0);
    }

    private void givenRiotFails(Throwable cause) {
        given(riotApiService.getAccountByPuuid(eq("p1"), eq(Platform.KR), any()))
                .willReturn(CompletableFuture.failedFuture(cause));
    }

    private RiotRateLimitException rateLimit() {
        return new RiotRateLimitException(Duration.ofSeconds(1), HttpStatus.TOO_MANY_REQUESTS, "", null);
    }

    private double count(String result) {
        var counter = meterRegistry.find("lol.renewal.step").tags("step", "account", "result", result).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    @DisplayName("게임 이름·태그를 저장하고 계정 비트를 켠 뒤 마무리한다")
    void run_savesGameIdentityAndMarksAccount() {
        // when
        step.run(msg("r1"));

        // then
        verify(summonerRepositoryPort).updateGameIdentity("p1", "Hide on bush", "KR1");
        verify(finisher).finish("p1", RenewalTransition.NONE);
    }

    @Test
    @DisplayName("마지막 단계면 SUCCEEDED 로 마무리한다")
    void run_lastStep_finishesWithSucceeded() {
        // given
        given(recordPort.markSteps("p1", "r1", EnumSet.of(RenewalStep.ACCOUNT)))
                .willReturn(RenewalTransition.SUCCEEDED);

        // when
        step.run(msg("r1"));

        // then
        verify(finisher).finish("p1", RenewalTransition.SUCCEEDED);
    }

    @Test
    @DisplayName("Riot 404 면 not_found 로 실패시킨다")
    void run_accountNotFound_failsNotFound() {
        // given
        givenRiotFails(new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "", null));

        // when
        step.run(msg("r1"));

        // then
        verify(finisher).fail("p1", "r1", "not_found");
        verify(summonerRepositoryPort, never()).updateGameIdentity(anyString(), anyString(), anyString());
        assertThat(count("failed")).isEqualTo(1);
    }

    @Test
    @DisplayName("첫 429 는 시도 수를 올려 계정 큐에 다시 넣는다")
    void run_riot429FirstAttempt_requeuesWithNextAttempt() {
        // given
        givenRiotFails(rateLimit());

        // when
        step.run(msg("r1"));

        // then
        verify(stepPublisher).publish(RabbitMqBinding.RENEWAL_ACCOUNT, new RenewalStepMessage("r1", "p1", "KR", 0, 1));
        verify(finisher, never()).fail(anyString(), anyString(), anyString());
        assertThat(count("requeue")).isEqualTo(1);
    }

    @Test
    @DisplayName("세 번째 429 는 riot_429 로 실패시킨다")
    void run_riot429ThirdAttempt_failsRiot429() {
        // given
        givenRiotFails(rateLimit());

        // when
        step.run(new RenewalStepMessage("r1", "p1", "KR", 0, 2));

        // then
        verify(finisher).fail("p1", "r1", "riot_429");
        verifyNoInteractions(stepPublisher);
    }

    @Test
    @DisplayName("예약 실패는 시도 수를 올리지 않고 잠시 뒤 다시 넣는다")
    void run_budgetExceeded_requeuesLaterWithoutAttemptIncrease() {
        // given
        givenRiotFails(new RiotBudgetExceededException("asia", RiotCallPriority.USER));

        // when
        step.run(msg("r1"));

        // then
        verify(stepPublisher).requeueLater(RabbitMqBinding.RENEWAL_ACCOUNT, msg("r1"));
        verify(finisher, never()).fail(anyString(), anyString(), anyString());
        assertThat(count("requeue")).isEqualTo(1);
    }

    @Test
    @DisplayName("레코드의 renewalId 가 다르면 Riot 을 부르지 않고 stale 로 건너뛴다")
    void run_staleRenewal_skipsWithoutRiotCall() {
        // given
        givenRecord("r2");

        // when
        step.run(msg("r1"));

        // then
        verify(finisher).skipped("stale");
        verifyNoInteractions(riotApiService, stepPublisher);
        assertThat(count("skipped")).isEqualTo(1);
    }

    @Test
    @DisplayName("레코드 조회가 던지면 Riot 을 부르지 않고 failed 를 센 뒤 정상 반환한다")
    void run_recordLookupThrows_countsFailedAndReturnsNormally() {
        // given
        given(recordPort.find("p1")).willThrow(new IllegalStateException("redis down"));

        // when // then
        assertThatCode(() -> step.run(msg("r1"))).doesNotThrowAnyException();
        verifyNoInteractions(riotApiService, stepPublisher);
        assertThat(count("failed")).isEqualTo(1);
    }

    @Test
    @DisplayName("stale 기록이 던져도 failed 를 센 뒤 정상 반환한다")
    void run_staleSkippedThrows_countsFailedAndReturnsNormally() {
        // given
        givenRecord("r2");
        doThrow(new IllegalStateException("meter down")).when(finisher).skipped(anyString());

        // when // then
        assertThatCode(() -> step.run(msg("r1"))).doesNotThrowAnyException();
        verifyNoInteractions(riotApiService, stepPublisher);
        assertThat(count("failed")).isEqualTo(1);
        assertThat(count("skipped")).isZero();
    }

    @Test
    @DisplayName("성공하면 단계 지표 success 를 센다")
    void run_success_countsStepMetric() {
        // when
        step.run(msg("r1"));

        // then
        assertThat(count("success")).isEqualTo(1);
    }

    @Test
    @DisplayName("그 밖의 예외는 분류한 reason 으로 실패시킨다")
    void run_otherException_failsWithClassifiedReason() {
        // given
        given(riotApiService.getAccountByPuuid(eq("p1"), eq(Platform.KR), any()))
                .willThrow(new IllegalStateException("boom"));

        // when
        step.run(msg("r1"));

        // then
        verify(finisher).fail("p1", "r1", "other");
    }

    @Test
    @DisplayName("Riot 호출은 USER 우선순위이고 429 인라인 재시도가 꺼져 있다")
    void run_riotCallIsUserPriorityWithoutRateLimitRetry() {
        // given
        List<RiotCallPriority> priorities = new ArrayList<>();
        List<Boolean> retryEnabled = new ArrayList<>();
        given(riotApiService.getAccountByPuuid(eq("p1"), eq(Platform.KR), any())).willAnswer(inv -> {
            priorities.add(RiotCallScope.current());
            retryEnabled.add(RiotRetryScope.rateLimitRetryEnabled());
            return CompletableFuture.completedFuture(new AccountDto());
        });

        // when
        step.run(msg("r1"));

        // then
        assertThat(priorities).containsExactly(RiotCallPriority.USER);
        assertThat(retryEnabled).containsExactly(false);
    }

    @Test
    @DisplayName("조회 뒤 단계에서 예외가 나면 레코드를 실패시키고 정상 반환한다")
    void run_exceptionAfterFetch_failsRecordAndReturnsNormally() {
        // given
        doThrow(new IllegalStateException("db down"))
                .when(summonerRepositoryPort).updateGameIdentity(anyString(), anyString(), anyString());

        // when
        step.run(msg("r1"));

        // then
        verify(finisher).fail("p1", "r1", "other");
        assertThat(count("failed")).isEqualTo(1);
    }

    @Test
    @DisplayName("실패 표시 자체가 던져도 정상 반환한다")
    void run_failItselfThrows_stillReturnsNormally() {
        // given
        doThrow(new IllegalStateException("db down"))
                .when(summonerRepositoryPort).updateGameIdentity(anyString(), anyString(), anyString());
        doThrow(new IllegalStateException("redis down"))
                .when(finisher).fail(anyString(), anyString(), anyString());

        // when // then
        assertThatCode(() -> step.run(msg("r1"))).doesNotThrowAnyException();
    }
}
