package com.mmrtr.lol.infra.rabbitmq.renewal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import com.mmrtr.lol.infra.rabbitmq.service.MatchQueueRank;
import com.mmrtr.lol.infra.rabbitmq.service.MessageSender;
import com.mmrtr.lol.infra.redis.service.RenewalMatchClaimService;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermit;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

class RenewalMatchStepTest {

    private static final long START_TIME = 1_767_225_600L;
    private static final EnumSet<RenewalStep> MATCH = EnumSet.of(RenewalStep.MATCH);
    private static final List<String> TWENTY = IntStream.rangeClosed(1, 20).mapToObj(i -> "m" + i).toList();
    private static final String MISSING = "lol.renewal.match_missing";

    private final RenewalRecordPort recordPort = mock(RenewalRecordPort.class);
    private final MatchApiPort matchApiPort = mock(MatchApiPort.class);
    private final RiotPermitPort permitPort = RiotPermitFixtures.grantingPort();
    private final MatchRepositoryPort matchRepositoryPort = mock(MatchRepositoryPort.class);
    private final RenewalMatchSaver saver = mock(RenewalMatchSaver.class);
    private final MatchCacheWritePort matchCacheWritePort = mock(MatchCacheWritePort.class);
    private final RenewalMatchClaimService claimService = mock(RenewalMatchClaimService.class);
    private final MessageSender messageSender = mock(MessageSender.class);
    private final RenewalStepPublisher stepPublisher = mock(RenewalStepPublisher.class);
    private final RenewalFinisher finisher = mock(RenewalFinisher.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final Executor executor = Runnable::run;
    private final RenewalMatchStep step = step(true);

    /** backgroundEnabled = lol.renewal.background.enabled (추가 매치 찾기 넘기기). */
    private RenewalMatchStep step(boolean backgroundEnabled) {
        return new RenewalMatchStep(
                recordPort, matchApiPort, permitPort, new RenewalMatchFetcher(matchApiPort, permitPort, executor),
                matchRepositoryPort, saver, matchCacheWritePort, claimService, messageSender,
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
        given(claimService.findClaimedByOthers(anyCollection(), eq("r1"))).willReturn(Set.of());
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

    private static RenewalStepMessage msg(String renewalId, long startTime) {
        return new RenewalStepMessage(renewalId, "p1", "KR", startTime, 0);
    }

    private double count(String name, String... tags) {
        var counter = meterRegistry.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    @DisplayName("새 매치를 받으면 캐시를 쓰고 매치 비트를 켠 뒤 DB 저장을 넘긴다")
    void run_newMatches_cachesMarksThenSaves() {
        // when
        step.run(msg("r1", START_TIME));

        // then
        List<MatchDto> fetched = List.of(match("m1"), match("m2"));
        List<TimelineDto> fetchedTimelines = List.of(timeline("m1"), timeline("m2"));
        InOrder order = inOrder(matchCacheWritePort, recordPort, finisher, saver);
        order.verify(matchCacheWritePort).writeMatches(fetched, fetchedTimelines, "p1");
        order.verify(recordPort).markSteps("p1", "r1", MATCH);
        order.verify(finisher).finish("p1", RenewalTransition.NONE);
        order.verify(saver).save(fetched, fetchedTimelines);
        verifyNoInteractions(messageSender);
        verify(stepPublisher, never()).publishMatchFind(any());
        assertThat(count("lol.renewal.step", "step", "match", "result", "success")).isEqualTo(1);
    }

    @Test
    @DisplayName("매치 id 는 메시지의 startTime 부터 0번째 20개를 받는다")
    void run_idsUseStartTimeFromMessage() {
        // when
        step.run(msg("r1", START_TIME));

        // then
        verify(matchApiPort).fetchMatchIdsByPuuid(eq("p1"), eq("KR"), eq(START_TIME), eq(0), eq(20), any(),
                eq(executor));
    }

    @Test
    @DisplayName("앞 3개만 타임라인까지 받고, 나머지는 나중 타임라인으로 넘긴다")
    void run_firstThreeGetTimelines_restGoToTimelineLater() {
        // given
        givenIds(TWENTY);

        // when
        step.run(msg("r1", START_TIME));

        // then
        verify(matchApiPort, times(3)).fetchTimelineById(anyString(), eq("KR"), any(), any());
        for (String matchId : List.of("m1", "m2", "m3")) {
            verify(matchApiPort).fetchTimelineById(eq(matchId), eq("KR"), any(), any());
        }
        verify(messageSender, times(17)).sendMessageByMatchId(anyString(), eq("KR"),
                eq(MatchQueueRank.TIMELINE_LATER));
        for (String matchId : TWENTY.subList(3, 20)) {
            verify(messageSender).sendMessageByMatchId(matchId, "KR", MatchQueueRank.TIMELINE_LATER);
        }
    }

    @Test
    @DisplayName("id 가 20개면 DB 확인보다 먼저 매치 검색 큐에 넘긴다")
    void run_twentyIds_handsOffMatchFindBeforeDbCheck() {
        // given
        givenIds(TWENTY);

        // when
        step.run(msg("r1", START_TIME));

        // then
        InOrder order = inOrder(stepPublisher, matchRepositoryPort);
        order.verify(stepPublisher).publishMatchFind(any());
        order.verify(matchRepositoryPort).findExistingMatchIds(anyList());
    }

    @Test
    @DisplayName("추가 갱신을 끄면 id 가 20개여도 매치 검색 큐에 넘기지 않는다")
    void run_backgroundDisabled_twentyIds_doesNotHandOffToMatchFind() {
        // given
        givenIds(TWENTY);
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(TWENTY);

        // when
        step(false).run(msg("r1", START_TIME));

        // then
        verify(stepPublisher, never()).publishMatchFind(any());
    }

    @Test
    @DisplayName("DB 에 있는 매치는 빼고, 그 타임라인만 따로 받지 않는다")
    void run_existingInDb_excludedWithoutTimelineOnlyFetch() {
        // given
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(List.of("m1"));

        // when
        step.run(msg("r1", START_TIME));

        // then
        verify(matchApiPort, never()).fetchMatchById(eq("m1"), anyString(), any(), any());
        verify(matchApiPort, never()).fetchTimelineById(eq("m1"), anyString(), any(), any());
        verify(matchApiPort).fetchMatchById(eq("m2"), eq("KR"), any(), any());
        verify(matchRepositoryPort, never()).findMatchIdsWithTimeline(any());
    }

    @Test
    @DisplayName("다른 갱신이 쥔 매치는 빼고 기다리지 않는다")
    void run_claimedByOthers_excludedNotWaited() {
        // given
        given(claimService.findClaimedByOthers(anyCollection(), eq("r1"))).willReturn(Set.of("m1"));

        // when
        step.run(msg("r1", START_TIME));

        // then
        verify(matchApiPort, never()).fetchMatchById(eq("m1"), anyString(), any(), any());
        verify(recordPort).markSteps("p1", "r1", MATCH);
        assertThat(count(MISSING, "reason", "claimed_by_other")).isEqualTo(1);
    }

    @Test
    @DisplayName("새 매치가 없으면 Riot 을 부르지 않고 매치 비트를 켠다")
    void run_noNewMatches_marksMatchWithoutCalls() {
        // given
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(List.of("m1", "m2"));

        // when
        step.run(msg("r1", START_TIME));

        // then
        verify(matchApiPort, never()).fetchMatchById(anyString(), anyString(), any(), any());
        verify(recordPort).markSteps("p1", "r1", MATCH);
        verify(finisher).finish("p1", RenewalTransition.NONE);
    }

    @Test
    @DisplayName("1개까지 줄여도 허가가 거절된 매치는 선점하지 않고 사용자 재시도로 넘긴 뒤 갱신을 성공시킨다")
    void run_denied_queuesRestAsUserRetryAndSucceeds() {
        // given: m1~m3 묶음(3·2개)은 거절, m1 하나는 허가, 남은 m2·m3(2·1개)은 거절
        givenIds(List.of("m1", "m2", "m3"));
        RiotPermits oneMatch = RiotPermits.of(List.of(
                new RiotPermit("t1", "KR", RiotMethod.MATCH, RiotCallPriority.USER),
                new RiotPermit("t2", "KR", RiotMethod.MATCH_TIMELINE, RiotCallPriority.USER)));
        given(permitPort.tryAcquire(eq("KR"), eq(RiotCallPriority.USER),
                argThat(counts -> counts.containsKey(RiotMethod.MATCH))))
                .willReturn(Optional.empty(), Optional.empty(), Optional.of(oneMatch), Optional.empty());

        // when
        step.run(msg("r1", START_TIME));

        // then
        verify(matchApiPort).fetchMatchById(eq("m1"), eq("KR"), any(), any());
        verify(messageSender).sendMessageByMatchId("m2", "KR", MatchQueueRank.USER_RETRY);
        verify(messageSender).sendMessageByMatchId("m3", "KR", MatchQueueRank.USER_RETRY);
        verify(claimService, never()).claim("m2", "r1");
        verify(claimService, never()).claim("m3", "r1");
        verify(recordPort).markSteps("p1", "r1", MATCH);
        assertThat(count(MISSING, "reason", "denied_queued")).isEqualTo(2);
    }

    @Test
    @DisplayName("거절된 매치 넘기기가 실패하면 other 로 실패시키되 받은 매치는 저장한다")
    void run_deniedHandOffFails_failsOtherButStillSaves() {
        // given: m1 만 허가, m2 는 거절
        RiotPermits oneMatch = RiotPermits.of(List.of(
                new RiotPermit("t1", "KR", RiotMethod.MATCH, RiotCallPriority.USER),
                new RiotPermit("t2", "KR", RiotMethod.MATCH_TIMELINE, RiotCallPriority.USER)));
        given(permitPort.tryAcquire(eq("KR"), eq(RiotCallPriority.USER),
                argThat(counts -> counts.containsKey(RiotMethod.MATCH))))
                .willReturn(Optional.empty(), Optional.of(oneMatch), Optional.empty());
        doThrow(new IllegalStateException("mq down"))
                .when(messageSender).sendMessageByMatchId("m2", "KR", MatchQueueRank.USER_RETRY);

        // when // then
        assertThatCode(() -> step.run(msg("r1", START_TIME))).doesNotThrowAnyException();
        verify(finisher).fail("p1", "r1", "other");
        verify(recordPort, never()).markSteps(anyString(), anyString(), any());
        verify(saver).save(List.of(match("m1")), List.of(timeline("m1")));
    }

    @Test
    @DisplayName("부른 뒤 실패한 매치는 누락으로 두고 넘기지 않으며 갱신은 성공한다")
    void run_callFailures_leftMissingAndSucceeds() {
        // given
        given(matchApiPort.fetchMatchById(eq("m1"), eq("KR"), any(), any())).willReturn(
                CompletableFuture.failedFuture(new RiotServerException(HttpStatus.SERVICE_UNAVAILABLE, "")));

        // when
        step.run(msg("r1", START_TIME));

        // then
        verify(messageSender, never()).sendMessageByMatchId(eq("m1"), anyString(), any());
        verify(recordPort).markSteps("p1", "r1", MATCH);
        verify(saver).save(List.of(match("m2")), List.of(timeline("m2")));
        assertThat(count(MISSING, "reason", "call_failed")).isEqualTo(1);
    }

    @Test
    @DisplayName("캐시 쓰기가 실패하면 other 로 실패시키되 받은 매치는 저장한다")
    void run_cacheWriteFails_failsOtherButStillSaves() {
        // given
        doThrow(new IllegalStateException("cache.match.write failed"))
                .when(matchCacheWritePort).writeMatches(anyList(), anyList(), eq("p1"));

        // when // then
        assertThatCode(() -> step.run(msg("r1", START_TIME))).doesNotThrowAnyException();
        verify(finisher).fail("p1", "r1", "other");
        verify(recordPort, never()).markSteps(anyString(), anyString(), any());
        verify(saver).save(List.of(match("m1"), match("m2")), List.of(timeline("m1"), timeline("m2")));
    }

    @Test
    @DisplayName("나중 타임라인 발행이 실패해도 갱신을 실패시키지 않는다")
    void run_timelineLaterPublishFailure_doesNotFailRenewal() {
        // given: m4·m5 는 타임라인 없이 받아 나중 타임라인으로 간다. m4 발행이 실패한다
        givenIds(List.of("m1", "m2", "m3", "m4", "m5"));
        doThrow(new IllegalStateException("mq down"))
                .when(messageSender).sendMessageByMatchId("m4", "KR", MatchQueueRank.TIMELINE_LATER);

        // when // then
        assertThatCode(() -> step.run(msg("r1", 0))).doesNotThrowAnyException();
        verify(recordPort).markSteps("p1", "r1", MATCH);
        verify(finisher, never()).fail(anyString(), anyString(), anyString());
        verify(messageSender).sendMessageByMatchId("m5", "KR", MatchQueueRank.TIMELINE_LATER);
        assertThat(count("lol.renewal.timeline_later.publish_failed")).isEqualTo(1);
        assertThat(count("lol.renewal.step", "step", "match", "result", "success")).isEqualTo(1);
    }

    @Test
    @DisplayName("Riot 이 같은 id 를 두 번 줘도 한 번만 부른다")
    void run_duplicateIds_calledOnce() {
        // given
        givenIds(List.of("m1", "m1", "m2"));

        // when
        step.run(msg("r1", START_TIME));

        // then
        verify(matchApiPort, times(1)).fetchMatchById(eq("m1"), eq("KR"), any(), any());
    }

    @Test
    @DisplayName("재전달이면 자기가 쥔 선점 때문에 빠지지 않고 다시 부른다")
    void run_redelivered_ownClaimsStillFetched() {
        // when
        step.run(msg("r1", START_TIME));

        // then
        verify(claimService).findClaimedByOthers(anyCollection(), eq("r1"));
        verify(claimService).claim("m1", "r1");
        verify(matchApiPort).fetchMatchById(eq("m1"), eq("KR"), any(), any());
    }

    @Test
    @DisplayName("id 조회 허가가 거절되면 Riot 을 부르지 않고 시도 수를 올리지 않은 채 잠시 뒤 다시 넣는다")
    void deniedRequeuesLaterWithoutAttempt() {
        // given
        RiotPermitFixtures.denyAll(permitPort);

        // when
        step.run(msg("r1", START_TIME));

        // then
        verify(stepPublisher).requeueLater(RabbitMqBinding.RENEWAL_MATCH, msg("r1", START_TIME));
        verify(stepPublisher, never()).publish(any(), any());
        verifyNoInteractions(matchApiPort, claimService, saver);
    }

    @Test
    @DisplayName("레코드의 renewalId 가 다르면 Riot 을 부르지 않고 stale 로 건너뛴다")
    void run_staleRenewal_skipsWithoutRiotCall() {
        // when
        step.run(msg("r2", 0));

        // then
        verify(finisher).skipped("stale");
        verifyNoInteractions(matchApiPort, permitPort, claimService, saver);
    }

    @Test
    @DisplayName("레코드 조회가 던지면 Riot 을 부르지 않고 정상 반환한다")
    void run_recordLookupThrows_returnsNormally() {
        // given
        given(recordPort.find("p1")).willThrow(new IllegalStateException("redis down"));

        // when // then
        assertThatCode(() -> step.run(msg("r1", 0))).doesNotThrowAnyException();
        verifyNoInteractions(matchApiPort, claimService);
        assertThat(count("lol.renewal.step", "step", "match", "result", "failed")).isEqualTo(1);
    }

    @Test
    @DisplayName("알 수 없는 platform 이면 other 로 실패시킨다")
    void run_unknownPlatform_failsOther() {
        // when
        step.run(new RenewalStepMessage("r1", "p1", "XX", 0, 0));

        // then
        verify(finisher).fail("p1", "r1", "other");
        verifyNoInteractions(matchApiPort);
    }

    @Test
    @DisplayName("처리 중 예상 못 한 예외는 other 로 실패시키고 밖으로 던지지 않는다")
    void run_unexpectedException_failsAndReturns() {
        // given
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willThrow(new IllegalStateException("db down"));

        // when // then
        assertThatCode(() -> step.run(msg("r1", START_TIME))).doesNotThrowAnyException();
        verify(finisher).fail("p1", "r1", "other");
        verifyNoInteractions(saver);
    }
}
