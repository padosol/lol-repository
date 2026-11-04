package com.mmrtr.lol.infra.rabbitmq.renewal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerMessage;
import com.mmrtr.lol.infra.rabbitmq.service.SummonerRevisionChecker;
import com.mmrtr.lol.infra.rabbitmq.service.SummonerRevisionChecker.RevisionCheckResult;
import com.mmrtr.lol.infra.riot.dto.summoner.SummonerDto;
import com.mmrtr.lol.infra.riot.exception.RiotBudgetExceededException;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotRetryScope;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RenewalSummonerStepTest {

    private static final int ICON = 4321;
    private static final long LEVEL = 321L;
    private static final long RIOT_REVISION = 1_700_000_000_000L;
    private static final long DB_REVISION_SECONDS = 1_600_000_000L;

    private final RenewalRecordPort recordPort = mock(RenewalRecordPort.class);
    private final SummonerRepositoryPort summonerRepositoryPort = mock(SummonerRepositoryPort.class);
    private final SummonerRevisionChecker revisionChecker = mock(SummonerRevisionChecker.class);
    private final RiotApiService riotApiService = mock(RiotApiService.class);
    private final RenewalStepPublisher stepPublisher = mock(RenewalStepPublisher.class);
    private final RenewalFinisher finisher = mock(RenewalFinisher.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final Executor executor = Runnable::run;
    private final RenewalSummonerStep step = new RenewalSummonerStep(
            recordPort, summonerRepositoryPort, revisionChecker, riotApiService, stepPublisher, finisher,
            meterRegistry, executor);

    private SummonerDto summonerDto;

    @BeforeEach
    void setUp() {
        summonerDto = new SummonerDto();
        summonerDto.setPuuid("p1");
        summonerDto.setProfileIconId(ICON);
        summonerDto.setSummonerLevel(LEVEL);
        summonerDto.setRevisionDate(RIOT_REVISION);
        givenRecord("r1", RenewalStatus.PROGRESS, null);
        given(riotApiService.getSummonerByPuuid(eq("p1"), eq(Platform.KR), any()))
                .willReturn(CompletableFuture.completedFuture(summonerDto));
        given(revisionChecker.check("p1", summonerDto)).willReturn(new RevisionCheckResult(true, DB_REVISION_SECONDS));
        given(summonerRepositoryPort.updateStatusInfo("p1", ICON, LEVEL)).willReturn(true);
    }

    private void givenRecord(String renewalId, RenewalStatus status, String reason) {
        given(recordPort.find("p1")).willReturn(Optional.of(
                new RenewalRecord(renewalId, status, reason, 0, false, 1_000L, 6_000L, null, "KR")));
    }

    private SummonerMessage message(String renewalId) {
        return new SummonerMessage("KR", "p1", 0L, renewalId, 0);
    }

    private void givenRiotFails(Throwable cause) {
        given(riotApiService.getSummonerByPuuid(eq("p1"), eq(Platform.KR), any()))
                .willReturn(CompletableFuture.failedFuture(cause));
    }

    private RiotRateLimitException rateLimit() {
        return new RiotRateLimitException(Duration.ofSeconds(1), HttpStatus.TOO_MANY_REQUESTS, "", null);
    }

    @Test
    @DisplayName("revisionDate 가 같으면 네 단계를 한 번에 켜 끝낸다")
    void run_revisionUnchanged_marksAllStepsAndFinishes() {
        // given
        given(revisionChecker.check("p1", summonerDto)).willReturn(new RevisionCheckResult(false, DB_REVISION_SECONDS));
        given(recordPort.markSteps("p1", "r1", EnumSet.allOf(RenewalStep.class)))
                .willReturn(RenewalTransition.SUCCEEDED);

        // when
        step.run(message("r1"));

        // then
        verify(finisher).finish("p1", RenewalTransition.SUCCEEDED);
        verifyNoInteractions(stepPublisher);
        verify(summonerRepositoryPort, never()).updateStatusInfo(anyString(), org.mockito.ArgumentMatchers.anyInt(),
                anyLong());
    }

    @Test
    @DisplayName("revisionDate 가 다르면 아이콘·레벨을 저장하고 소환사 비트를 켠 뒤 세 단계 큐로 나눠 보낸다")
    void run_revisionChanged_savesStatusMarksSummonerAndPublishesThreeSteps() {
        // when
        step.run(message("r1"));

        // then
        verify(summonerRepositoryPort).updateStatusInfo("p1", ICON, LEVEL);
        verify(recordPort).saveRiotRevisionDate("p1", "r1", RIOT_REVISION);
        verify(recordPort).markSteps("p1", "r1", EnumSet.of(RenewalStep.SUMMONER));
        verify(stepPublisher).publish(RabbitMqBinding.RENEWAL_ACCOUNT, new RenewalStepMessage("r1", "p1", "KR", 0, 0));
        verify(stepPublisher).publish(RabbitMqBinding.RENEWAL_LEAGUE, new RenewalStepMessage("r1", "p1", "KR", 0, 0));
        verify(stepPublisher).publish(RabbitMqBinding.RENEWAL_MATCH,
                new RenewalStepMessage("r1", "p1", "KR", DB_REVISION_SECONDS, 0));
    }

    @Test
    @DisplayName("레코드의 renewalId 가 다르면 Riot 을 부르지 않고 stale 로 건너뛴다")
    void run_staleRenewalId_skipsWithoutRiotCall() {
        // given
        givenRecord("r2", RenewalStatus.PROGRESS, null);

        // when
        step.run(message("r1"));

        // then
        verify(finisher).skipped("stale");
        verifyNoInteractions(riotApiService, stepPublisher);
    }

    @Test
    @DisplayName("기한으로 FAILED 가 된 레코드도 같은 renewalId 면 조회를 이어 간다")
    void run_sameRenewalIdAfterFailed_stillRuns() {
        // given
        givenRecord("r1", RenewalStatus.FAILED, "deadline");

        // when
        step.run(message("r1"));

        // then
        verify(riotApiService).getSummonerByPuuid(eq("p1"), eq(Platform.KR), any());
        verify(finisher, never()).skipped(anyString());
    }

    @Test
    @DisplayName("renewalId 가 없는 옛 메시지는 레코드를 열지 않고 stale 로 건너뛴다")
    void run_messageWithoutRenewalId_skipsStale() {
        // when
        step.run(message(null));

        // then
        verify(finisher).skipped("stale");
        verifyNoInteractions(riotApiService, stepPublisher);
        verify(recordPort, never()).markSteps(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("레코드가 없으면 stale 로 건너뛴다")
    void run_noRecord_skipsStale() {
        // given
        given(recordPort.find("p1")).willReturn(Optional.empty());

        // when
        step.run(message("r1"));

        // then
        verify(finisher).skipped("stale");
        verifyNoInteractions(riotApiService);
    }

    @Test
    @DisplayName("레코드 조회가 던지면 Riot 을 부르지 않고 failed 를 센 뒤 정상 반환한다")
    void run_recordLookupThrows_countsFailedAndReturnsNormally() {
        // given
        given(recordPort.find("p1")).willThrow(new IllegalStateException("redis down"));

        // when // then
        assertThatCode(() -> step.run(message("r1"))).doesNotThrowAnyException();
        verifyNoInteractions(riotApiService, stepPublisher);
        assertThat(meterRegistry.get("lol.renewal.step").tags("step", "summoner", "result", "failed")
                .counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("알 수 없는 platform 이면 other 로 실패시킨다")
    void run_unknownPlatform_failsOther() {
        // when
        step.run(new SummonerMessage("XX", "p1", 0L, "r1", 0));

        // then
        verify(finisher).fail("p1", "r1", "other");
        verifyNoInteractions(riotApiService, stepPublisher);
    }

    @Test
    @DisplayName("DB 에 소환사 행이 없으면 not_found 로 실패시키고 발행하지 않는다")
    void run_summonerRowMissing_failsNotFound() {
        // given
        given(summonerRepositoryPort.updateStatusInfo("p1", ICON, LEVEL)).willReturn(false);

        // when
        step.run(message("r1"));

        // then
        verify(finisher).fail("p1", "r1", "not_found");
        verifyNoInteractions(stepPublisher);
        verify(recordPort, never()).saveRiotRevisionDate(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Riot 404 면 not_found 로 실패시킨다")
    void run_riot404_failsNotFound() {
        // given
        givenRiotFails(new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "", null));

        // when
        step.run(message("r1"));

        // then
        verify(finisher).fail("p1", "r1", "not_found");
    }

    @Test
    @DisplayName("첫 429 는 시도 수를 올려 소환사 큐에 다시 넣고 실패시키지 않는다")
    void run_riot429FirstAttempt_requeuesWithNextAttempt() {
        // given
        givenRiotFails(rateLimit());

        // when
        step.run(message("r1"));

        // then
        verify(stepPublisher).republishSummoner(org.mockito.ArgumentMatchers.argThat(
                m -> m.getAttempt() == 1 && "r1".equals(m.getRenewalId()) && "p1".equals(m.getPuuid())));
        verify(finisher, never()).fail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("세 번째 429 는 riot_429 로 실패시킨다")
    void run_riot429ThirdAttempt_failsRiot429() {
        // given
        givenRiotFails(rateLimit());

        // when
        step.run(new SummonerMessage("KR", "p1", 0L, "r1", 2));

        // then
        verify(finisher).fail("p1", "r1", "riot_429");
        verifyNoInteractions(stepPublisher);
    }

    @Test
    @DisplayName("예약 실패는 같은 메시지를 잠시 뒤 다시 넣는다")
    void run_budgetExceeded_requeuesLater() {
        // given
        givenRiotFails(new RiotBudgetExceededException("asia", RiotCallPriority.USER));
        SummonerMessage message = message("r1");

        // when
        step.run(message);

        // then
        verify(stepPublisher).republishSummonerLater(message);
        verify(finisher, never()).fail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("그 밖의 예외는 분류한 reason 으로 실패시킨다")
    void run_otherException_failsWithClassifiedReason() {
        // given
        given(riotApiService.getSummonerByPuuid(eq("p1"), eq(Platform.KR), any()))
                .willThrow(new IllegalStateException("boom"));

        // when
        step.run(message("r1"));

        // then
        verify(finisher).fail("p1", "r1", "other");
    }

    @Test
    @DisplayName("CompletionException 으로 감싸인 404 도 not_found 로 본다")
    void run_wrappedNotFound_failsNotFound() {
        // given
        given(riotApiService.getSummonerByPuuid(eq("p1"), eq(Platform.KR), any()))
                .willThrow(new CompletionException(new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "", null)));

        // when
        step.run(message("r1"));

        // then
        verify(finisher).fail("p1", "r1", "not_found");
    }

    @Test
    @DisplayName("Riot 호출은 429 인라인 재시도가 꺼져 있다")
    void run_riotCallsWithoutRateLimitRetry() {
        // given
        List<Boolean> retryEnabled = new ArrayList<>();
        given(riotApiService.getSummonerByPuuid(eq("p1"), eq(Platform.KR), any())).willAnswer(inv -> {
            retryEnabled.add(RiotRetryScope.rateLimitRetryEnabled());
            return CompletableFuture.completedFuture(summonerDto);
        });

        // when
        step.run(message("r1"));

        // then
        assertThat(retryEnabled).containsExactly(false);
    }

    @Test
    @DisplayName("조회 뒤 단계에서 예외가 나면 레코드를 실패시키고 정상 반환한다")
    void run_exceptionAfterFetch_failsRecordAndReturnsNormally() {
        // given
        given(recordPort.markSteps("p1", "r1", EnumSet.of(RenewalStep.SUMMONER)))
                .willThrow(new IllegalStateException("db down"));

        // when
        step.run(message("r1"));

        // then
        verify(finisher).fail("p1", "r1", "other");
        verifyNoInteractions(stepPublisher);
    }

    @Test
    @DisplayName("발행이 던져도 레코드를 실패시키고 정상 반환한다")
    void run_publishThrows_failsRecordAndReturnsNormally() {
        // given
        org.mockito.Mockito.doThrow(new IllegalStateException("rabbit down"))
                .when(stepPublisher).publish(any(), any());

        // when
        step.run(message("r1"));

        // then
        verify(finisher).fail("p1", "r1", "other");
    }

    @Test
    @DisplayName("실패 표시 자체가 던져도 정상 반환한다")
    void run_failItselfThrows_stillReturnsNormally() {
        // given
        given(recordPort.markSteps("p1", "r1", EnumSet.of(RenewalStep.SUMMONER)))
                .willThrow(new IllegalStateException("db down"));
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
                .when(finisher).fail(anyString(), anyString(), anyString());

        // when // then
        org.assertj.core.api.Assertions.assertThatCode(() -> step.run(message("r1"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("단계 지표를 summoner 태그로 센다")
    void run_countsStepMetricWithSummonerTag() {
        // when
        step.run(message("r1"));
        step.run(message("r2"));

        // then
        assertThat(meterRegistry.get("lol.renewal.step").tags("step", "summoner", "result", "success")
                .counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get("lol.renewal.step").tags("step", "summoner", "result", "skipped")
                .counter().count()).isEqualTo(1);
    }
}
