package com.mmrtr.lol.infra.rabbitmq.renewal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
import com.mmrtr.lol.infra.rabbitmq.RiotPermitFixtures;
import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.RenewalStepMessage;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.rabbitmq.service.MatchQueueRank;
import com.mmrtr.lol.infra.rabbitmq.service.MessageSender;
import com.mmrtr.lol.infra.redis.service.RenewalMatchClaimService;
import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

class RenewalMatchStepTest {

    private static final long START_TIME = 1_767_225_600L;
    private static final EnumSet<RenewalStep> MATCH = EnumSet.of(RenewalStep.MATCH);
    private static final Map<RiotMethod, Integer> IDS_COST = Map.of(RiotMethod.MATCH_IDS_BY_PUUID, 1);
    private static final List<String> TWENTY = IntStream.rangeClosed(1, 20).mapToObj(i -> "m" + i).toList();

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
    private final RenewalMatchStep step = step(true);

    /** backgroundEnabled = lol.renewal.background.enabled (추가 매치 찾기·백필 넘기기). */
    private RenewalMatchStep step(boolean backgroundEnabled) {
        return new RenewalMatchStep(
                recordPort, matchApiPort, permitPort, new RenewalMatchFetcher(matchApiPort, permitPort, executor),
                matchRepositoryPort, saveMatchDataUseCase, matchCacheWritePort, claimService, messageSender,
                stepPublisher, finisher, meterRegistry, executor, backgroundEnabled);
    }

    private final Map<String, MatchDto> matches = new HashMap<>();
    private final Map<String, TimelineDto> timelines = new HashMap<>();

    @BeforeEach
    void setUp() {
        given(recordPort.find("p1")).willReturn(Optional.of(
                new RenewalRecord("r1", RenewalStatus.PROGRESS, null, 2, null, 1_000L, 6_000L, null, "KR")));
        givenIds(List.of("m1", "m2"));
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(List.of());
        given(matchRepositoryPort.findMatchIdsWithTimeline(anyList())).willReturn(Set.of());
        given(claimService.findExcluded(anyCollection())).willReturn(Set.of());
        given(claimService.claim(anyString(), eq("r1"))).willReturn(true);
        given(matchApiPort.fetchMatchById(anyString(), eq("KR"), any(), any()))
                .willAnswer(inv -> CompletableFuture.completedFuture(match(inv.getArgument(0))));
        given(matchApiPort.fetchTimelineById(anyString(), eq("KR"), any(), any()))
                .willAnswer(inv -> CompletableFuture.completedFuture(timeline(inv.getArgument(0))));
        given(recordPort.markSteps("p1", "r1", MATCH)).willReturn(RenewalTransition.NONE);
    }

    private MatchDto match(String matchId) {
        return matches.computeIfAbsent(matchId, id -> new MatchDto());
    }

    private TimelineDto timeline(String matchId) {
        return timelines.computeIfAbsent(matchId, id -> new TimelineDto());
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

    /** id 목록 허가는 내주고, 매치 묶음 허가만 거절한다. */
    private void denyMatchBatches() {
        given(permitPort.tryAcquire(eq("KR"), eq(RiotCallPriority.USER),
                argThat(counts -> !counts.containsKey(RiotMethod.MATCH_IDS_BY_PUUID))))
                .willReturn(Optional.empty());
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
        List<MatchDto> saved = List.of(match("m1"), match("m2"));
        List<TimelineDto> savedTimelines = List.of(timeline("m1"), timeline("m2"));
        InOrder order = inOrder(saveMatchDataUseCase, matchCacheWritePort, claimService, finisher);
        order.verify(saveMatchDataUseCase).execute(saved, savedTimelines);
        order.verify(matchCacheWritePort).writeMatches(saved, savedTimelines, "p1");
        order.verify(claimService).release("m1", "r1");
        order.verify(claimService).release("m2", "r1");
        order.verify(finisher).finish("p1", RenewalTransition.NONE);
        verify(recordPort).markSteps("p1", "r1", MATCH);
        verifyNoInteractions(messageSender);
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
    @DisplayName("id 는 USER 허가 1건, 매치는 묶음 허가 하나로 부른다")
    void run_takesOnePermitForIdsAndOneBatchForMatches() {
        // when
        step.run(msg("r1", 0), false);

        // then
        verify(permitPort).tryAcquire("KR", RiotCallPriority.USER, IDS_COST);
        verify(permitPort).tryAcquire("KR", RiotCallPriority.USER,
                Map.of(RiotMethod.MATCH, 2, RiotMethod.MATCH_TIMELINE, 2));
        ArgumentCaptor<RiotPermits> m1 = ArgumentCaptor.forClass(RiotPermits.class);
        ArgumentCaptor<RiotPermits> m2 = ArgumentCaptor.forClass(RiotPermits.class);
        verify(matchApiPort).fetchMatchById(eq("m1"), eq("KR"), m1.capture(), eq(executor));
        verify(matchApiPort).fetchTimelineById(eq("m2"), eq("KR"), m2.capture(), eq(executor));
        assertThat(m1.getValue()).isSameAs(m2.getValue());
    }

    @Test
    @DisplayName("새 매치 20개를 받으면 저장은 한 번이고 타임라인은 최신 3개뿐이다")
    void savesOnceWithAtMostThreeTimelines() {
        // given
        givenIds(TWENTY);

        // when
        step.run(msg("r1", 0), false);

        // then
        ArgumentCaptor<List<MatchDto>> savedMatches = ArgumentCaptor.captor();
        ArgumentCaptor<List<TimelineDto>> savedTimelines = ArgumentCaptor.captor();
        verify(saveMatchDataUseCase, times(1)).execute(savedMatches.capture(), savedTimelines.capture());
        assertThat(savedMatches.getValue()).hasSize(20);
        assertThat(savedTimelines.getValue()).hasSizeLessThanOrEqualTo(3)
                .containsExactly(timeline("m1"), timeline("m2"), timeline("m3"));
        verify(matchApiPort, never()).fetchTimelineById(eq("m4"), any(), any(), any());
    }

    @Test
    @DisplayName("저장한 새 매치 중 타임라인을 받지 않은 17개는 나중 타임라인 순위로 하나씩 넘긴다")
    void untimelinedSavedMatchesGoToTimelineLater() {
        // given
        givenIds(TWENTY);

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(messageSender, times(17)).sendMessageByMatchId(anyString(), eq("KR"), eq(MatchQueueRank.TIMELINE_LATER));
        TWENTY.subList(3, 20).forEach(id ->
                verify(messageSender).sendMessageByMatchId(id, "KR", MatchQueueRank.TIMELINE_LATER));
        verify(messageSender, never()).sendMessageByMatchId(anyString(), anyString(), eq(MatchQueueRank.USER_RETRY));
        verify(recordPort).markSteps("p1", "r1", MATCH);
    }

    @Test
    @DisplayName("묶음 허가가 세 번 모두 거절되면 매치 대상은 사용자 재시도로, 타임라인만 필요한 것은 나중 타임라인으로 넘긴다")
    void deniedMatchesGoToUserRetry_deniedTimelineOnlyToTimelineLater() {
        // given: m2 는 매치가 DB 에 있고 타임라인만 없다
        givenIds(List.of("m1", "m2", "m3"));
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(List.of("m2"));
        denyMatchBatches();

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(messageSender).sendMessageByMatchId("m1", "KR", MatchQueueRank.USER_RETRY);
        verify(messageSender).sendMessageByMatchId("m3", "KR", MatchQueueRank.USER_RETRY);
        verify(messageSender).sendMessageByMatchId("m2", "KR", MatchQueueRank.TIMELINE_LATER);
        verify(messageSender, never()).sendMessageByMatchId("m2", "KR", MatchQueueRank.USER_RETRY);
        verify(matchApiPort, never()).fetchMatchById(anyString(), anyString(), any(), any());
        verify(matchApiPort, never()).fetchTimelineById(anyString(), anyString(), any(), any());
        verifyNoInteractions(saveMatchDataUseCase, matchCacheWritePort);
        verify(claimService).release("m1", "r1");
        verify(claimService).release("m3", "r1");
        verify(claimService, never()).claim(eq("m2"), anyString());
        verify(recordPort, never()).markSteps(any(), any(), eq(MATCH));
        assertThat(count("lol.renewal.step", "step", "match", "result", "success")).isEqualTo(1);
    }

    @Test
    @DisplayName("매치 묶음 허가를 묻다 예외가 나면 매치를 부르지 않고 사용자 재시도 순위로 넘긴다")
    void run_matchPermitCheckThrows_handsOffMatches() {
        // given
        given(permitPort.tryAcquire(eq("KR"), eq(RiotCallPriority.USER),
                argThat(counts -> counts.containsKey(RiotMethod.MATCH))))
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
    @DisplayName("타임라인만 넘겼어도 매치 대상이 모두 저장되면 매치 비트를 켜고, 그 타임라인은 한 번만 넘긴다")
    void matchBitSetWhenAllMatchTargetsSaved_evenIfTimelinesHandedOff() {
        // given
        givenTimeline("m1", CompletableFuture.failedFuture(
                new RiotServerException(HttpStatus.SERVICE_UNAVAILABLE, "")));

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(saveMatchDataUseCase).execute(List.of(match("m1"), match("m2")), List.of(timeline("m2")));
        verify(messageSender, times(1)).sendMessageByMatchId("m1", "KR", MatchQueueRank.TIMELINE_LATER);
        verify(messageSender, never()).sendMessageByMatchId(anyString(), anyString(), eq(MatchQueueRank.USER_RETRY));
        verify(recordPort).markSteps("p1", "r1", MATCH);
    }

    @Test
    @DisplayName("매치 대상을 하나라도 넘기면 매치 비트는 판정기에 맡긴다 (429 → 사용자 재시도, 선점 해제)")
    void matchBitLeftToJudgeWhenAnyMatchHandedOff() {
        // given
        givenMatch("m1", CompletableFuture.failedFuture(rateLimit()));

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(messageSender).sendMessageByMatchId("m1", "KR", MatchQueueRank.USER_RETRY);
        verify(messageSender, never()).sendMessageByMatchId("m1", "KR", MatchQueueRank.TIMELINE_LATER);
        verify(claimService).release("m1", "r1");
        verify(claimService, never()).markFailed(anyString());
        verify(saveMatchDataUseCase).execute(List.of(match("m2")), List.of(timeline("m2")));
        verify(recordPort, never()).markSteps(any(), any(), eq(MATCH));
    }

    @Test
    @DisplayName("4xx 받은 매치는 실패 표시를 남기고 선점을 풀며 넘기지 않는다")
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
    @DisplayName("다른 갱신이 선점한 매치라도 최신 3게임이면 타임라인만 받고, 매치 비트는 켜지 않는다")
    void claimedElsewhereTimelineTargetStillFetchesTimeline() {
        // given
        given(claimService.claim("m1", "r1")).willReturn(false);

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(matchApiPort, never()).fetchMatchById(eq("m1"), any(), any(), any());
        verify(matchApiPort).fetchTimelineById(eq("m1"), eq("KR"), any(), any());
        verify(saveMatchDataUseCase).execute(List.of(match("m2")), List.of(timeline("m1"), timeline("m2")));
        verify(claimService, never()).release(eq("m1"), anyString());
        verifyNoInteractions(messageSender);
        verify(recordPort, never()).markSteps(any(), any(), eq(MATCH));
    }

    @Test
    @DisplayName("다른 갱신이 선점한 매치가 최신 3게임 밖이면 부르지 않고 매치 비트를 켜지 않는다")
    void run_matchClaimedByOtherRenewal_skipsFetchAndLeavesMatchStepOpen() {
        // given
        givenIds(List.of("m1", "m2", "m3", "m4"));
        given(claimService.claim("m4", "r1")).willReturn(false);

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(matchApiPort, never()).fetchMatchById(eq("m4"), any(), any(), any());
        verify(matchApiPort, never()).fetchTimelineById(eq("m4"), any(), any(), any());
        verify(claimService, never()).release(eq("m4"), anyString());
        verify(messageSender, never()).sendMessageByMatchId(eq("m4"), anyString(), any());
        verify(recordPort, never()).markSteps(any(), any(), eq(MATCH));
    }

    @Test
    @DisplayName("매치가 전부 DB 에 있고 최신 타임라인도 있으면 빈 대상을 기록하고 조회 없이 매치 비트를 켠다")
    void run_noNewMatches_recordsEmptyTargetsAndMarksMatch() {
        // given
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(List.of("m1", "m2"));
        given(matchRepositoryPort.findMatchIdsWithTimeline(List.of("m1", "m2"))).willReturn(Set.of("m1", "m2"));

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(recordPort).recordTargets("p1", "r1", Set.of());
        verify(recordPort).markSteps("p1", "r1", MATCH);
        verifyNoInteractions(saveMatchDataUseCase, matchCacheWritePort);
        verify(permitPort).tryAcquire("KR", RiotCallPriority.USER, IDS_COST);
        verify(matchApiPort, never()).fetchMatchById(anyString(), anyString(), any(), any());
        verify(matchApiPort, never()).fetchTimelineById(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("매치는 DB 에 있고 최신 타임라인만 없으면 타임라인만 받아 저장하고 매치 비트를 켠다")
    void run_existingMatchesMissingTimeline_savesTimelinesOnly() {
        // given
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(List.of("m1", "m2"));

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(recordPort).recordTargets("p1", "r1", Set.of());
        verify(matchApiPort, never()).fetchMatchById(anyString(), anyString(), any(), any());
        verify(saveMatchDataUseCase).execute(List.of(), List.of(timeline("m1"), timeline("m2")));
        verify(claimService, never()).claim(anyString(), anyString());
        verifyNoInteractions(messageSender);
        verify(recordPort).markSteps("p1", "r1", MATCH);
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
    @DisplayName("추가 갱신을 끄면 계속 실패해 빠진 매치는 대상에서만 빼고 백필로 넘기지 않는다")
    void run_backgroundDisabled_excludedMatchNotHandedOff() {
        // given
        given(claimService.findExcluded(anyCollection())).willReturn(Set.of("m1"));

        // when
        step(false).run(msg("r1", 0), false);

        // then
        verify(recordPort).recordTargets("p1", "r1", Set.of("m2"));
        verify(matchApiPort, never()).fetchMatchById(eq("m1"), any(), any(), any());
        verify(messageSender, never()).sendMessageByMatchId(eq("m1"), anyString(), any());
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
        givenIds(TWENTY);
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(TWENTY);

        // when
        step.run(msg("r1", START_TIME), false);

        // then
        verify(stepPublisher).publishMatchFind(new SummonerRenewalMessage("p1", "KR", START_TIME, 20));
    }

    @Test
    @DisplayName("추가 갱신을 끄면 id 가 20개여도 매치 검색 큐에 넘기지 않는다")
    void run_backgroundDisabled_twentyIds_doesNotHandOffToMatchFind() {
        // given
        givenIds(TWENTY);
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(TWENTY);

        // when
        step(false).run(msg("r1", START_TIME), false);

        // then
        verify(stepPublisher, never()).publishMatchFind(any());
    }

    @Test
    @DisplayName("첫 전달에서 저장이 실패하면 다시 던져 재전달받고, 선점은 쥔 채 아무것도 넘기지 않는다")
    void run_saveFails_rethrowsForRedelivery() {
        // given
        givenIds(TWENTY);
        IllegalStateException boom = new IllegalStateException("db down");
        doThrow(boom).when(saveMatchDataUseCase).execute(anyList(), anyList());

        // when // then
        assertThatThrownBy(() -> step.run(msg("r1", 0), false)).isSameAs(boom);
        verify(recordPort, never()).markSteps(any(), any(), any());
        verify(claimService, never()).release(anyString(), anyString());
        verify(finisher, never()).fail(anyString(), anyString(), anyString());
        verifyNoInteractions(matchCacheWritePort, stepPublisher, messageSender);
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
                .when(messageSender).sendMessageByMatchId(anyString(), anyString(), eq(MatchQueueRank.USER_RETRY));

        // when // then
        assertThatCode(() -> step.run(msg("r1", 0), false)).doesNotThrowAnyException();
        verify(finisher).fail("p1", "r1", "other");
        verify(claimService).release("m1", "r1");
    }

    @Test
    @DisplayName("나중 타임라인 발행이 실패해도 매치 대상을 모두 저장했으면 매치 비트를 켜고 갱신을 실패시키지 않는다")
    void timelineLaterPublishFailure_doesNotFailRenewal() {
        // given: m4·m5 는 타임라인 없이 저장돼 나중 타임라인으로 간다. m4 발행이 실패한다
        givenIds(List.of("m1", "m2", "m3", "m4", "m5"));
        doThrow(new IllegalStateException("mq down"))
                .when(messageSender).sendMessageByMatchId("m4", "KR", MatchQueueRank.TIMELINE_LATER);

        // when // then
        assertThatCode(() -> step.run(msg("r1", 0), false)).doesNotThrowAnyException();
        verify(recordPort).markSteps("p1", "r1", MATCH);
        verify(finisher).finish("p1", RenewalTransition.NONE);
        verify(finisher, never()).fail(anyString(), anyString(), anyString());
        verify(messageSender).sendMessageByMatchId("m5", "KR", MatchQueueRank.TIMELINE_LATER);
        assertThat(count("lol.renewal.timeline_later.publish_failed")).isEqualTo(1);
        assertThat(count("lol.renewal.step", "step", "match", "result", "success")).isEqualTo(1);
        assertThat(count("lol.renewal.step", "step", "match", "result", "failed")).isZero();
    }

    @Test
    @DisplayName("사용자 재시도 발행과 실패 표시는 나중 타임라인 발행보다 먼저 하고, 나중 타임라인 발행 실패가 이를 건너뛰게 하지 않는다")
    void userRetryAndMarkFailedRunBeforeTimelineLater() {
        // given: m1 은 429 → 사용자 재시도, m2 는 400 → 실패 표시, m3 는 타임라인만 실패, m4 는 타임라인 대상 밖
        givenIds(List.of("m1", "m2", "m3", "m4"));
        givenMatch("m1", CompletableFuture.failedFuture(rateLimit()));
        givenMatch("m2", CompletableFuture.failedFuture(new RiotClientException(HttpStatus.BAD_REQUEST, "", null)));
        givenTimeline("m3", CompletableFuture.failedFuture(
                new RiotServerException(HttpStatus.SERVICE_UNAVAILABLE, "")));
        doThrow(new IllegalStateException("mq down"))
                .when(messageSender).sendMessageByMatchId(anyString(), anyString(), eq(MatchQueueRank.TIMELINE_LATER));

        // when // then
        assertThatCode(() -> step.run(msg("r1", 0), false)).doesNotThrowAnyException();
        InOrder order = inOrder(messageSender, claimService);
        order.verify(messageSender).sendMessageByMatchId("m1", "KR", MatchQueueRank.USER_RETRY);
        order.verify(claimService).markFailed("m2");
        order.verify(messageSender).sendMessageByMatchId("m3", "KR", MatchQueueRank.TIMELINE_LATER);
        order.verify(messageSender).sendMessageByMatchId("m4", "KR", MatchQueueRank.TIMELINE_LATER);
        verify(finisher, never()).fail(anyString(), anyString(), anyString());
        verify(recordPort, never()).markSteps(any(), any(), eq(MATCH));
        assertThat(count("lol.renewal.timeline_later.publish_failed")).isEqualTo(2);
        assertThat(count("lol.renewal.step", "step", "match", "result", "success")).isEqualTo(1);
    }

    @Test
    @DisplayName("거절된 매치 항목은 타임라인 대상이어도 사용자 재시도로만 넘기고 나중 타임라인으로는 넘기지 않는다")
    void deniedTimelineMatchItemsAreNotAlsoSentTimelineLater() {
        // given: m1·m2 는 새 매치이자 최신 3게임이라 match·timeline 을 함께 받는 항목이다
        denyMatchBatches();

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(messageSender).sendMessageByMatchId("m1", "KR", MatchQueueRank.USER_RETRY);
        verify(messageSender).sendMessageByMatchId("m2", "KR", MatchQueueRank.USER_RETRY);
        verify(messageSender, never())
                .sendMessageByMatchId(anyString(), anyString(), eq(MatchQueueRank.TIMELINE_LATER));
    }

    @Test
    @DisplayName("매치 조회 429 는 그 자리에서 다시 부르지 않는다")
    void run_match429_isNotRetriedInline() {
        // given
        givenMatch("m1", CompletableFuture.failedFuture(rateLimit()));

        // when
        step.run(msg("r1", 0), false);

        // then
        verify(matchApiPort, times(1)).fetchMatchById(eq("m1"), any(), any(), any());
        verify(permitPort, times(1)).tryAcquire(eq("KR"), eq(RiotCallPriority.USER),
                argThat(counts -> counts.containsKey(RiotMethod.MATCH)));
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
}
