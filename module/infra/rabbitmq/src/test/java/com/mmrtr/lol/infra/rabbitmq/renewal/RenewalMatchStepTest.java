package com.mmrtr.lol.infra.rabbitmq.renewal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.domain.match.application.port.MatchCacheWritePort;
import com.mmrtr.lol.domain.match.application.port.MatchRepositoryPort;
import com.mmrtr.lol.domain.match.application.usecase.SaveMatchDataUseCase;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;
import com.mmrtr.lol.domain.summoner.domain.RenewalRecord;
import com.mmrtr.lol.domain.summoner.domain.RenewalStatus;
import com.mmrtr.lol.domain.summoner.domain.RenewalStep;
import com.mmrtr.lol.domain.summoner.domain.RenewalTransition;
import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.RenewalStepMessage;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.rabbitmq.service.MatchQueueRank;
import com.mmrtr.lol.infra.rabbitmq.service.MessageSender;
import com.mmrtr.lol.infra.rabbitmq.RiotPermitFixtures;
import com.mmrtr.lol.infra.redis.service.RenewalMatchClaimService;
import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermit;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

class RenewalMatchStepTest {

    private static final long START_TIME = 1_767_225_600L;
    private static final EnumSet<RenewalStep> MATCH = EnumSet.of(RenewalStep.MATCH);
    private static final Map<RiotMethod, Integer> IDS_COST = Map.of(RiotMethod.MATCH_IDS_BY_PUUID, 1);
    private static final Map<RiotMethod, Integer> MATCH_COST =
            Map.of(RiotMethod.MATCH, 1, RiotMethod.MATCH_TIMELINE, 1);

    private final RenewalRecordPort recordPort = mock(RenewalRecordPort.class);
    private final MatchApiPort matchApiPort = mock(MatchApiPort.class);
    private final RiotPermitPort permitPort = RiotPermitFixtures.grantingPort();
    private final MatchRepositoryPort matchRepositoryPort = mock(MatchRepositoryPort.class);
    private final SaveMatchDataUseCase saveMatchDataUseCase = mock(SaveMatchDataUseCase.class);
    private final MatchCacheWritePort matchCacheWritePort = mock(MatchCacheWritePort.class);
    private final RenewalMatchClaimService claimService = mock(RenewalMatchClaimService.class);
    private final MessageSender messageSender = mock(MessageSender.class);
    private final RenewalStepPublisher stepPublisher = mock(RenewalStepPublisher.class);
    private final RenewalFinisher finisher = mock(RenewalFinisher.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final Executor executor = Runnable::run;
    private final RenewalMatchStep step = new RenewalMatchStep(
            recordPort, matchApiPort, permitPort, matchRepositoryPort, saveMatchDataUseCase, matchCacheWritePort,
            claimService, messageSender, stepPublisher, finisher, meterRegistry, executor);

    private final MatchDto match1 = new MatchDto();
    private final MatchDto match2 = new MatchDto();
    private final TimelineDto timeline1 = new TimelineDto();
    private final TimelineDto timeline2 = new TimelineDto();

    @BeforeEach
    void setUp() {
        given(recordPort.find("p1")).willReturn(Optional.of(
                new RenewalRecord("r1", RenewalStatus.PROGRESS, null, 2, false, 1_000L, 6_000L, null, "KR")));
        givenIds(List.of("m1", "m2"));
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(List.of());
        given(claimService.findExcluded(anyCollection())).willReturn(Set.of());
        given(claimService.claim(anyString(), eq("r1"))).willReturn(true);
        givenMatch("m1", CompletableFuture.completedFuture(match1));
        givenTimeline("m1", CompletableFuture.completedFuture(timeline1));
        givenMatch("m2", CompletableFuture.completedFuture(match2));
        givenTimeline("m2", CompletableFuture.completedFuture(timeline2));
        given(recordPort.markSteps("p1", "r1", MATCH)).willReturn(RenewalTransition.NONE);
    }

    private void givenIds(List<String> ids) {
        given(matchApiPort.fetchMatchIdsByPuuid(eq("p1"), eq("KR"), anyLong(), eq(0), eq(20), any(), any()))
                .willReturn(CompletableFuture.completedFuture(ids));
    }

    private void givenMatch(String matchId, CompletableFuture<MatchDto> result) {
        given(matchApiPort.fetchMatchById(eq(matchId), eq("KR"), any(), any())).willReturn(result);
    }

    private void givenTimeline(String matchId, CompletableFuture<TimelineDto> result) {
        given(matchApiPort.fetchTimelineById(eq(matchId), eq("KR"), any(), any())).willReturn(result);
    }

    private static RenewalStepMessage msg(String renewalId, long startTime) {
        return new RenewalStepMessage(renewalId, "p1", "KR", startTime, 0);
    }

    private static RiotRateLimitException rateLimit() {
        return new RiotRateLimitException(Duration.ofSeconds(1), null, HttpStatus.TOO_MANY_REQUESTS, "", null);
    }

    private double count(String name, String... tags) {
        var counter = meterRegistry.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    @DisplayName("새 매치를 모두 받으면 한 번에 저장하고 캐시를 쓴 뒤 선점을 풀고 매치 비트를 켠다")
    void run_allNewMatchesFetched_savesOnceAndMarksMatch() {
        // when
        step.run(msg("r1", START_TIME), false);

        // then
        verify(recordPort).recordTargets("p1", "r1", Set.of("m1", "m2"));
        InOrder order = inOrder(saveMatchDataUseCase, matchCacheWritePort, claimService, finisher);
        order.verify(saveMatchDataUseCase).execute(List.of(match1, match2), List.of(timeline1, timeline2));
        order.verify(matchCacheWritePort).writeMatches(List.of(match1, match2), List.of(timeline1, timeline2), "p1");
        order.verify(claimService).release("m1", "r1");
        order.verify(claimService).release("m2", "r1");
        order.verify(finisher).finish("p1", RenewalTransition.NONE);
        verify(recordPort).markSteps("p1", "r1", MATCH);
        verify(stepPublisher, never()).publishMatchFind(any());
        assertThat(count("lol.renewal.step", "step", "match", "result", "success")).isEqualTo(1);
    }

    @Test
    @DisplayName("매치 id 는 메시지의 startTime 부터 0번째 20개를 받는다")
    void run_idsUseStartTimeFromMessage() {
        // when
        step.run(msg("r1", START_TIME), false);

        // then
        verify(matchApiPort).fetchMatchIdsByPuuid(eq("p1"), eq("KR"), eq(START_TIME), eq(0), eq(20), any(),
                eq(executor));
    }

    @Test
    @DisplayName("매치가 전부 DB 에 있으면 빈 대상을 기록하고 조회 없이 매치 비트를 켠다")
    void run_noNewMatches_recordsEmptyTargetsAndMarksMatch() {
        // given
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(List.of("m1", "m2"));

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(recordPort).recordTargets("p1", "r1", Set.of());
        verify(recordPort).markSteps("p1", "r1", MATCH);
        verify(matchApiPort, never()).fetchMatchById(anyString(), anyString(), any(), any());
        verifyNoInteractions(saveMatchDataUseCase, matchCacheWritePort);
    }

    @Test
    @DisplayName("다른 갱신이 선점한 매치는 부르지 않고 매치 비트를 켜지 않는다")
    void run_matchClaimedByOtherRenewal_skipsFetchAndLeavesMatchStepOpen() {
        // given
        given(claimService.claim("m1", "r1")).willReturn(false);

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(matchApiPort, never()).fetchMatchById(eq("m1"), any(), any(), any());
        verify(matchApiPort, never()).fetchTimelineById(eq("m1"), any(), any(), any());
        verify(claimService, never()).release(eq("m1"), anyString());
        verify(saveMatchDataUseCase).execute(List.of(match2), List.of(timeline2));
        verify(recordPort, never()).markSteps(any(), any(), eq(MATCH));
    }

    @Test
    @DisplayName("429 받은 매치는 사용자 재시도 순위로 매치 적재 큐에 넘기고 선점을 푼다")
    void run_match429_sendsUserRetryAndReleasesWithoutMarkingMatch() {
        // given
        givenMatch("m1", CompletableFuture.failedFuture(rateLimit()));

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(messageSender).sendMessageByMatchId("m1", "KR", MatchQueueRank.USER_RETRY);
        verify(claimService).release("m1", "r1");
        verify(claimService, never()).markFailed(anyString());
        verify(saveMatchDataUseCase).execute(List.of(match2), List.of(timeline2));
        verify(recordPort, never()).markSteps(any(), any(), eq(MATCH));
    }

    @Test
    @DisplayName("허가가 거절된 매치는 부르지 않고 사용자 재시도 순위로 넘긴다")
    void run_matchPermitDenied_sendsUserRetryWithoutRiotCall() {
        // given: 첫 매치(m1)의 허가만 거절한다
        AtomicInteger matchRequests = new AtomicInteger();
        given(permitPort.tryAcquire(eq("KR"), eq(RiotCallPriority.USER), eq(MATCH_COST))).willAnswer(inv ->
                matchRequests.incrementAndGet() == 1 ? Optional.empty() : Optional.of(RiotPermits.of(List.of(
                        new RiotPermit("t-m", "KR", RiotMethod.MATCH, RiotCallPriority.USER),
                        new RiotPermit("t-t", "KR", RiotMethod.MATCH_TIMELINE,
                                RiotCallPriority.USER)))));

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(matchApiPort, never()).fetchMatchById(eq("m1"), any(), any(), any());
        verify(matchApiPort, never()).fetchTimelineById(eq("m1"), any(), any(), any());
        verify(messageSender).sendMessageByMatchId("m1", "KR", MatchQueueRank.USER_RETRY);
        verify(claimService).release("m1", "r1");
        verify(claimService, never()).markFailed(anyString());
        verify(saveMatchDataUseCase).execute(List.of(match2), List.of(timeline2));
        verify(recordPort, never()).markSteps(any(), any(), eq(MATCH));
    }

    @Test
    @DisplayName("매치 허가를 묻다 예외가 나면 그 매치만 부르지 않고 사용자 재시도 순위로 넘긴다")
    void run_matchPermitCheckThrows_handsOffThatMatch() {
        // given
        given(permitPort.tryAcquire(eq("KR"), eq(RiotCallPriority.USER), eq(MATCH_COST)))
                .willThrow(new IllegalStateException("redis down"));

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(matchApiPort, never()).fetchMatchById(anyString(), anyString(), any(), any());
        verify(messageSender).sendMessageByMatchId("m1", "KR", MatchQueueRank.USER_RETRY);
        verify(messageSender).sendMessageByMatchId("m2", "KR", MatchQueueRank.USER_RETRY);
        verifyNoInteractions(saveMatchDataUseCase);
        assertThat(count("lol.renewal.step", "step", "match", "result", "success")).isEqualTo(1);
    }

    @Test
    @DisplayName("4xx 받은 매치는 실패 표시를 남기고 선점을 푼다")
    void run_match4xx_marksFailedAndReleases() {
        // given
        givenMatch("m1", CompletableFuture.failedFuture(new RiotClientException(HttpStatus.BAD_REQUEST, "", null)));

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(claimService).markFailed("m1");
        verify(claimService).release("m1", "r1");
        verify(messageSender, never()).sendMessageByMatchId(anyString(), anyString(), any());
        verify(recordPort, never()).markSteps(any(), any(), eq(MATCH));
    }

    @Test
    @DisplayName("타임라인이 5xx 면 그 매치는 저장하지 않고 실패 표시 없이 사용자 재시도 순위로 넘긴다")
    void run_timeline5xx_handsOffAsUserRetryWithoutFailedMark() {
        // given
        givenTimeline("m1", CompletableFuture.failedFuture(
                new RiotServerException(HttpStatus.SERVICE_UNAVAILABLE, "")));

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(messageSender).sendMessageByMatchId("m1", "KR", MatchQueueRank.USER_RETRY);
        verify(claimService).release("m1", "r1");
        verify(claimService, never()).markFailed(anyString());
        verify(saveMatchDataUseCase).execute(List.of(match2), List.of(timeline2));
    }

    @Test
    @DisplayName("Riot 예외가 아닌 조회 실패(읽기 시간 초과 등)는 실패 표시 없이 사용자 재시도 순위로 넘긴다")
    void run_matchNonRiotFailure_handsOffAsUserRetryWithoutFailedMark() {
        // given
        givenMatch("m1", CompletableFuture.failedFuture(
                new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"))));

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(messageSender).sendMessageByMatchId("m1", "KR", MatchQueueRank.USER_RETRY);
        verify(claimService).release("m1", "r1");
        verify(claimService, never()).markFailed(anyString());
    }

    @Test
    @DisplayName("상태가 429 인 RiotClientException 은 4xx 로 보지 않고 사용자 재시도 순위로 넘긴다")
    void run_plainClientException429_handsOffAsUserRetry() {
        // given
        givenMatch("m1", CompletableFuture.failedFuture(
                new RiotClientException(HttpStatus.TOO_MANY_REQUESTS, "", null)));

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(messageSender).sendMessageByMatchId("m1", "KR", MatchQueueRank.USER_RETRY);
        verify(claimService, never()).markFailed(anyString());
    }

    @Test
    @DisplayName("한쪽이 429 여도 다른 쪽이 404 면 넘기지 않고 실패 표시를 남긴다")
    void run_match429AndTimeline404_marksFailed() {
        // given
        givenMatch("m1", CompletableFuture.failedFuture(rateLimit()));
        givenTimeline("m1", CompletableFuture.failedFuture(
                new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "", null)));

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(claimService).markFailed("m1");
        verify(claimService).release("m1", "r1");
        verify(messageSender, never()).sendMessageByMatchId(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("계속 실패해 빠진 매치는 대상에 넣지 않고 부르지 않으며, 매치 적재 큐에 백필 순위로 넘기고 match_excluded 를 센다")
    void run_excludedMatch_notTargetedAndHandedOffAsBackfill() {
        // given
        given(claimService.findExcluded(anyCollection())).willReturn(Set.of("m1"));

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(recordPort).recordTargets("p1", "r1", Set.of("m2"));
        verify(claimService, never()).claim(eq("m1"), anyString());
        verify(matchApiPort, never()).fetchMatchById(eq("m1"), any(), any(), any());
        verify(matchApiPort, never()).fetchTimelineById(eq("m1"), any(), any(), any());
        verify(messageSender).sendMessageByMatchId("m1", "KR", MatchQueueRank.BACKFILL);
        verify(messageSender, never()).sendMessageByMatchId(eq("m2"), anyString(), any());
        verify(recordPort).markSteps("p1", "r1", MATCH);
        assertThat(count("lol.renewal.match_excluded")).isEqualTo(1);
    }

    @Test
    @DisplayName("빠진 매치를 넘기지 못하면 갱신을 실패시켜 커서가 그 매치를 지나가지 않게 한다")
    void run_excludedHandOffFails_failsRenewal() {
        // given
        given(claimService.findExcluded(anyCollection())).willReturn(Set.of("m1"));
        doThrow(new IllegalStateException("mq down"))
                .when(messageSender).sendMessageByMatchId("m1", "KR", MatchQueueRank.BACKFILL);

        // when // then
        assertThatCode(() -> step.run(msg("r1", 0), false)).doesNotThrowAnyException();
        verify(finisher).fail("p1", "r1", "other");
        verify(recordPort, never()).markSteps(any(), any(), any());
    }

    @Test
    @DisplayName("id 가 20개면 21번째부터 찾도록 매치 검색 큐에 넘긴다")
    void run_twentyIds_handsOffToMatchFindFromOffset20() {
        // given
        List<String> ids = IntStream.range(0, 20).mapToObj(i -> "x" + i).toList();
        givenIds(ids);
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(ids);

        // when
        step.run(msg("r1", START_TIME), false);

        // then
        verify(stepPublisher).publishMatchFind(new SummonerRenewalMessage("p1", "KR", START_TIME, 20));
    }

    @Test
    @DisplayName("첫 전달에서 저장이 실패하면 다시 던져 재전달받고, 선점은 쥔 채 둔다")
    void run_saveFails_rethrowsForRedelivery() {
        // given
        IllegalStateException boom = new IllegalStateException("db down");
        doThrow(boom).when(saveMatchDataUseCase).execute(anyList(), anyList());

        // when // then
        assertThatThrownBy(() -> step.run(msg("r1", 0), false)).isSameAs(boom);
        verify(recordPort, never()).markSteps(any(), any(), any());
        verify(claimService, never()).release(anyString(), anyString());
        verify(finisher, never()).fail(anyString(), anyString(), anyString());
        verifyNoInteractions(matchCacheWritePort, stepPublisher);
    }

    @Test
    @DisplayName("재전달된 메시지에서도 저장이 실패하면 갱신을 실패시키고 선점을 푼 뒤 정상 반환한다")
    void run_saveFailsOnRedelivery_failsAndReturns() {
        // given
        doThrow(new IllegalStateException("db down")).when(saveMatchDataUseCase).execute(anyList(), anyList());

        // when // then
        assertThatCode(() -> step.run(msg("r1", 0), true)).doesNotThrowAnyException();
        verify(finisher).fail("p1", "r1", "other");
        verify(claimService).release("m1", "r1");
        verify(claimService).release("m2", "r1");
        verify(recordPort, never()).markSteps(any(), any(), any());
        assertThat(count("lol.renewal.step", "step", "match", "result", "failed")).isEqualTo(1);
    }

    @Test
    @DisplayName("저장 밖의 예상 못 한 예외는 갱신을 실패시키고 선점을 푼 뒤 정상 반환한다")
    void run_unexpectedException_failsReleasesAndReturns() {
        // given
        givenMatch("m1", CompletableFuture.failedFuture(rateLimit()));
        doThrow(new IllegalStateException("mq down"))
                .when(messageSender).sendMessageByMatchId(anyString(), anyString(), any());

        // when // then
        assertThatCode(() -> step.run(msg("r1", 0), false)).doesNotThrowAnyException();
        verify(finisher).fail("p1", "r1", "other");
        verify(claimService).release("m1", "r1");
    }

    @Test
    @DisplayName("id 조회의 첫 429 는 시도 수를 올려 매치 단계 큐에 다시 넣는다")
    void run_ids429FirstAttempt_requeuesWithNextAttempt() {
        // given
        given(matchApiPort.fetchMatchIdsByPuuid(eq("p1"), eq("KR"), anyLong(), eq(0), eq(20), any(), any()))
                .willReturn(CompletableFuture.failedFuture(rateLimit()));

        // when
        step.run(msg("r1", START_TIME), false);

        // then
        verify(stepPublisher).publish(RabbitMqBinding.RENEWAL_MATCH,
                new RenewalStepMessage("r1", "p1", "KR", START_TIME, 1));
        verify(recordPort, never()).recordTargets(anyString(), anyString(), anyCollection());
    }

    @Test
    @DisplayName("id 조회 허가가 거절되면 Riot 을 부르지 않고 시도 수를 올리지 않은 채 잠시 뒤 다시 넣는다")
    void deniedRequeuesLaterWithoutAttempt() {
        // given
        RiotPermitFixtures.denyAll(permitPort);

        // when
        step.run(msg("r1", START_TIME), false);

        // then
        verify(stepPublisher).requeueLater(RabbitMqBinding.RENEWAL_MATCH, msg("r1", START_TIME));
        verify(stepPublisher, never()).publish(any(), any());
        verifyNoInteractions(matchApiPort);
        verify(recordPort, never()).recordTargets(anyString(), anyString(), anyCollection());
    }

    @Test
    @DisplayName("레코드의 renewalId 가 다르면 Riot 을 부르지 않고 stale 로 건너뛴다")
    void run_staleRenewal_skipsWithoutRiotCall() {
        // when
        step.run(msg("r2", 0), false);

        // then
        verify(finisher).skipped("stale");
        verifyNoInteractions(matchApiPort, permitPort, claimService, saveMatchDataUseCase);
    }

    @Test
    @DisplayName("레코드 조회가 던지면 Riot 을 부르지 않고 정상 반환한다")
    void run_recordLookupThrows_returnsNormally() {
        // given
        given(recordPort.find("p1")).willThrow(new IllegalStateException("redis down"));

        // when // then
        assertThatCode(() -> step.run(msg("r1", 0), false)).doesNotThrowAnyException();
        verifyNoInteractions(matchApiPort, claimService);
        assertThat(count("lol.renewal.step", "step", "match", "result", "failed")).isEqualTo(1);
    }

    @Test
    @DisplayName("알 수 없는 platform 이면 other 로 실패시킨다")
    void run_unknownPlatform_failsOther() {
        // when
        step.run(new RenewalStepMessage("r1", "p1", "XX", 0, 0), false);

        // then
        verify(finisher).fail("p1", "r1", "other");
        verifyNoInteractions(matchApiPort);
    }

    @Test
    @DisplayName("id 는 USER 허가 1건, 매치는 매치마다 따로 받은 match·timeline 허가로 부른다")
    void run_takesUserPermitsForIdsAndPerMatch() {
        // when
        step.run(msg("r1", 0), false);

        // then
        verify(permitPort).tryAcquire("KR", RiotCallPriority.USER, IDS_COST);
        verify(permitPort, times(2)).tryAcquire("KR", RiotCallPriority.USER, MATCH_COST);
        ArgumentCaptor<RiotPermits> m1Match = ArgumentCaptor.forClass(RiotPermits.class);
        ArgumentCaptor<RiotPermits> m1Timeline = ArgumentCaptor.forClass(RiotPermits.class);
        ArgumentCaptor<RiotPermits> m2Match = ArgumentCaptor.forClass(RiotPermits.class);
        verify(matchApiPort).fetchMatchById(eq("m1"), eq("KR"), m1Match.capture(), any());
        verify(matchApiPort).fetchTimelineById(eq("m1"), eq("KR"), m1Timeline.capture(), any());
        verify(matchApiPort).fetchMatchById(eq("m2"), eq("KR"), m2Match.capture(), any());
        assertThat(m1Match.getValue()).isSameAs(m1Timeline.getValue());
        assertThat(m1Match.getValue()).isNotSameAs(m2Match.getValue());
    }
}
