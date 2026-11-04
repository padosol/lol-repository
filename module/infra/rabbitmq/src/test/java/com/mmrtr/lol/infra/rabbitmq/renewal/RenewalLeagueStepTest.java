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
import com.mmrtr.lol.domain.summoner.application.usecase.SaveSummonerDataUseCase;
import com.mmrtr.lol.domain.summoner.domain.RenewalRecord;
import com.mmrtr.lol.domain.summoner.domain.RenewalStatus;
import com.mmrtr.lol.domain.summoner.domain.RenewalStep;
import com.mmrtr.lol.domain.summoner.domain.RenewalTransition;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;
import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.RenewalStepMessage;
import com.mmrtr.lol.infra.riot.dto.league.LeagueEntryDto;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class RenewalLeagueStepTest {

    private final RenewalRecordPort recordPort = mock(RenewalRecordPort.class);
    private final SaveSummonerDataUseCase saveSummonerDataUseCase = mock(SaveSummonerDataUseCase.class);
    private final RiotApiService riotApiService = mock(RiotApiService.class);
    private final RenewalStepPublisher stepPublisher = mock(RenewalStepPublisher.class);
    private final RenewalFinisher finisher = mock(RenewalFinisher.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final Executor executor = Runnable::run;
    private final RenewalLeagueStep step = new RenewalLeagueStep(
            recordPort, saveSummonerDataUseCase, riotApiService, stepPublisher, finisher, meterRegistry, executor);

    @BeforeEach
    void setUp() {
        givenRecord("r1");
        LeagueEntryDto entry = new LeagueEntryDto();
        entry.setLeagueId("league-1");
        entry.setQueueType("RANKED_SOLO_5x5");
        entry.setTier("GOLD");
        entry.setRank("I");
        entry.setLeaguePoints(50);
        entry.setWins(10);
        entry.setLosses(5);
        entry.setHotStreak(true);
        entry.setVeteran(true);
        entry.setFreshBlood(true);
        entry.setInactive(true);
        given(riotApiService.getLeagueEntriesByPuuid(eq("p1"), eq(Platform.KR), any()))
                .willReturn(CompletableFuture.completedFuture(Set.of(entry)));
        given(recordPort.markSteps("p1", "r1", EnumSet.of(RenewalStep.LEAGUE))).willReturn(RenewalTransition.NONE);
    }

    private void givenRecord(String renewalId) {
        given(recordPort.find("p1")).willReturn(Optional.of(
                new RenewalRecord(renewalId, RenewalStatus.PROGRESS, null, 0, false, 1_000L, 6_000L, null, "KR")));
    }

    private RenewalStepMessage msg(String renewalId) {
        return new RenewalStepMessage(renewalId, "p1", "KR", 0, 0);
    }

    private void givenRiotFails(Throwable cause) {
        given(riotApiService.getLeagueEntriesByPuuid(eq("p1"), eq(Platform.KR), any()))
                .willReturn(CompletableFuture.failedFuture(cause));
    }

    private double count(String result) {
        var counter = meterRegistry.find("lol.renewal.step").tags("step", "league", "result", result).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    @DisplayName("리그 항목을 LeagueInfo 로 바꿔 저장하고 리그 비트를 켠다")
    @SuppressWarnings("unchecked")
    void run_mapsEntriesAndSavesLeagues() {
        // when
        step.run(msg("r1"));

        // then
        ArgumentCaptor<Set<LeagueInfo>> captor = ArgumentCaptor.forClass(Set.class);
        verify(saveSummonerDataUseCase).saveLeagues(eq("p1"), captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(info -> {
            assertThat(info.getLeagueId()).isEqualTo("league-1");
            assertThat(info.getQueueType()).isEqualTo("RANKED_SOLO_5x5");
            assertThat(info.getTier()).isEqualTo("GOLD");
            assertThat(info.getRank()).isEqualTo("I");
            assertThat(info.getLeaguePoints()).isEqualTo(50);
            assertThat(info.getWins()).isEqualTo(10);
            assertThat(info.getLosses()).isEqualTo(5);
            assertThat(info.isHotStreak()).isTrue();
            assertThat(info.isVeteran()).isTrue();
            assertThat(info.isFreshBlood()).isTrue();
            assertThat(info.isInactive()).isTrue();
        });
        verify(recordPort).markSteps("p1", "r1", EnumSet.of(RenewalStep.LEAGUE));
        verify(finisher).finish("p1", RenewalTransition.NONE);
        assertThat(count("success")).isEqualTo(1);
    }

    @Test
    @DisplayName("마지막 단계면 SUCCEEDED 로 마무리한다")
    void run_lastStep_finishesWithSucceeded() {
        // given
        given(recordPort.markSteps("p1", "r1", EnumSet.of(RenewalStep.LEAGUE)))
                .willReturn(RenewalTransition.SUCCEEDED);

        // when
        step.run(msg("r1"));

        // then
        verify(finisher).finish("p1", RenewalTransition.SUCCEEDED);
    }

    @Test
    @DisplayName("리그 항목이 없어도 빈 집합으로 저장하고 단계를 끝낸다")
    void run_noEntries_savesEmptyAndMarks() {
        // given
        given(riotApiService.getLeagueEntriesByPuuid(eq("p1"), eq(Platform.KR), any()))
                .willReturn(CompletableFuture.completedFuture(Set.of()));

        // when
        step.run(msg("r1"));

        // then
        verify(saveSummonerDataUseCase).saveLeagues("p1", Set.of());
        verify(recordPort).markSteps("p1", "r1", EnumSet.of(RenewalStep.LEAGUE));
    }

    @Test
    @DisplayName("Riot 404 면 not_found 로 실패시킨다")
    void run_notFound_failsNotFound() {
        // given
        givenRiotFails(new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "", null));

        // when
        step.run(msg("r1"));

        // then
        verify(finisher).fail("p1", "r1", "not_found");
        verify(saveSummonerDataUseCase, never()).saveLeagues(anyString(), any());
    }

    @Test
    @DisplayName("첫 429 는 시도 수를 올려 리그 큐에 다시 넣는다")
    void run_riot429FirstAttempt_requeuesWithNextAttempt() {
        // given
        givenRiotFails(new RiotRateLimitException(Duration.ofSeconds(1), HttpStatus.TOO_MANY_REQUESTS, "", null));

        // when
        step.run(msg("r1"));

        // then
        verify(stepPublisher).publish(RabbitMqBinding.RENEWAL_LEAGUE, new RenewalStepMessage("r1", "p1", "KR", 0, 1));
        verify(finisher, never()).fail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("세 번째 429 는 riot_429 로 실패시킨다")
    void run_riot429ThirdAttempt_failsRiot429() {
        // given
        givenRiotFails(new RiotRateLimitException(Duration.ofSeconds(1), HttpStatus.TOO_MANY_REQUESTS, "", null));

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
        verify(stepPublisher).requeueLater(RabbitMqBinding.RENEWAL_LEAGUE, msg("r1"));
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
        verifyNoInteractions(riotApiService, stepPublisher, saveSummonerDataUseCase);
        assertThat(count("skipped")).isEqualTo(1);
    }

    @Test
    @DisplayName("레코드 조회가 던지면 Riot 을 부르지 않고 failed 를 센 뒤 정상 반환한다")
    void run_recordLookupThrows_countsFailedAndReturnsNormally() {
        // given
        given(recordPort.find("p1")).willThrow(new IllegalStateException("redis down"));

        // when // then
        assertThatCode(() -> step.run(msg("r1"))).doesNotThrowAnyException();
        verifyNoInteractions(riotApiService, stepPublisher, saveSummonerDataUseCase);
        assertThat(count("failed")).isEqualTo(1);
    }

    @Test
    @DisplayName("Riot 호출은 429 인라인 재시도가 꺼져 있다")
    void run_riotCallWithoutRateLimitRetry() {
        // given
        List<Boolean> retryEnabled = new ArrayList<>();
        given(riotApiService.getLeagueEntriesByPuuid(eq("p1"), eq(Platform.KR), any())).willAnswer(inv -> {
            retryEnabled.add(RiotRetryScope.rateLimitRetryEnabled());
            return CompletableFuture.completedFuture(Set.<LeagueEntryDto>of());
        });

        // when
        step.run(msg("r1"));

        // then
        assertThat(retryEnabled).containsExactly(false);
    }

    @Test
    @DisplayName("저장에서 예외가 나면 레코드를 실패시키고, 실패 표시가 던져도 정상 반환한다")
    void run_saveThrows_failsRecordAndReturnsNormally() {
        // given
        doThrow(new IllegalStateException("db down")).when(saveSummonerDataUseCase).saveLeagues(anyString(), any());
        doThrow(new IllegalStateException("redis down")).when(finisher).fail(anyString(), anyString(), anyString());

        // when // then
        assertThatCode(() -> step.run(msg("r1"))).doesNotThrowAnyException();
        verify(finisher).fail("p1", "r1", "other");
        assertThat(count("failed")).isEqualTo(1);
    }
}
