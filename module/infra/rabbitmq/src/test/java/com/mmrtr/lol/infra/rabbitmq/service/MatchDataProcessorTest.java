package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.domain.match.application.port.MatchRepositoryPort;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.MetadataTimeLineDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.infra.rabbitmq.RiotPermitFixtures;
import com.mmrtr.lol.infra.redis.service.MatchRedisService;
import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchDataProcessorTest {

    private static final Map<RiotMethod, Integer> MATCH_COST =
            Map.of(RiotMethod.MATCH, 1, RiotMethod.MATCH_TIMELINE, 1);

    /** match·timeline 호출이 받은 허가 묶음 (부른 순서). */
    private final List<RiotPermits> seen = new ArrayList<>();
    private final RiotPermitPort permitPort = RiotPermitFixtures.grantingPort();
    private MatchRedisService redis;
    private MatchBatchProcessor batch;
    private MessageSender messageSender;
    private CompletableFuture<MatchDto> matchResult;
    private CompletableFuture<TimelineDto> timelineResult;
    private MatchRepositoryPort matchRepository;
    private SimpleMeterRegistry meters;
    private MatchApiPort port;
    private MatchDataProcessor processor;

    @BeforeEach
    void setUp() {
        redis = mock(MatchRedisService.class);
        batch = mock(MatchBatchProcessor.class);
        messageSender = mock(MessageSender.class);
        matchRepository = mock(MatchRepositoryPort.class);
        meters = new SimpleMeterRegistry();
        when(redis.tryMarkProcessing(anyString())).thenReturn(true);
        when(batch.hasCapacity()).thenReturn(true);
        when(batch.add(any(), any())).thenReturn(true);
        matchResult = CompletableFuture.completedFuture(new MatchDto());
        timelineResult = CompletableFuture.completedFuture(new TimelineDto());

        port = new MatchApiPort() {
            @Override
            public CompletableFuture<List<String>> fetchMatchIdsByPuuid(
                    String p, String pl, long s, int st, int c, RiotPermits permits, Executor e) {
                return CompletableFuture.completedFuture(List.of());
            }

            @Override
            public CompletableFuture<MatchDto> fetchMatchById(String id, String pl, RiotPermits permits, Executor e) {
                seen.add(permits);
                return matchResult;
            }

            @Override
            public CompletableFuture<TimelineDto> fetchTimelineById(
                    String id, String pl, RiotPermits permits, Executor e) {
                seen.add(permits);
                return timelineResult;
            }
        };
        processor = new MatchDataProcessor(port, permitPort, redis, batch, messageSender, Runnable::run,
                matchRepository, meters);
    }

    /** 저장할 수 있는 타임라인 — metadata.matchId 가 있다. */
    private static TimelineDto timelineOf(String matchId) {
        MetadataTimeLineDto metadata = new MetadataTimeLineDto();
        metadata.setMatchId(matchId);
        TimelineDto timeline = new TimelineDto();
        timeline.setMetadata(metadata);
        return timeline;
    }

    private static RiotRateLimitException rateLimit() {
        return new RiotRateLimitException(Duration.ofSeconds(1), null, HttpStatus.TOO_MANY_REQUESTS, "", null);
    }

    @Test
    @DisplayName("백필 순위는 BACKGROUND 로 match·timeline 허가를 함께 받아 그 허가로 부른다")
    void process_backfill_callsWithBackgroundPriority() {
        processor.process("KR_1", "KR", MatchQueueRank.BACKFILL);

        verify(permitPort).tryAcquire("KR", RiotCallPriority.BACKGROUND, MATCH_COST);
        assertThat(seen).hasSize(2);
        assertThat(seen.get(0)).isSameAs(seen.get(1));
    }

    @Test
    @DisplayName("사용자 재시도 순위는 USER 로 허가를 받는다")
    void process_userRetry_callsWithUserPriority() {
        processor.process("KR_1", "KR", MatchQueueRank.USER_RETRY);

        verify(permitPort).tryAcquire("KR", RiotCallPriority.USER, MATCH_COST);
        assertThat(seen).hasSize(2);
    }

    @Test
    @DisplayName("이미 DB 에 있으면 Riot 호출 없이 DUPLICATE 이다")
    void process_alreadyInDb_duplicateWithoutRiotCall() {
        when(matchRepository.findExistingMatchIds(List.of("KR_1"))).thenReturn(List.of("KR_1"));

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.USER_RETRY))
                .isEqualTo(MatchDataProcessor.Result.DUPLICATE);
        assertThat(seen).isEmpty();
        verifyNoInteractions(redis, permitPort);
        assertThat(count("user_retry", "duplicate")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("DB 확인이 던지면 Riot 호출·처리 표시 없이 FAILURE 이고 failure 로 센다")
    void process_dbCheckThrows_failureWithoutRiotCall() {
        when(matchRepository.findExistingMatchIds(List.of("KR_1"))).thenThrow(new IllegalStateException("db down"));

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.USER_RETRY))
                .isEqualTo(MatchDataProcessor.Result.FAILURE);
        assertThat(seen).isEmpty();
        verifyNoInteractions(redis, batch);
        assertThat(count("user_retry", "failure")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("순위와 결과별로 센다")
    void process_countsByRankAndResult() {
        processor.process("KR_1", "KR", MatchQueueRank.USER_RETRY);
        when(batch.add(any(), any())).thenReturn(false);
        processor.process("KR_2", "KR", MatchQueueRank.BACKFILL);

        assertThat(count("user_retry", "success")).isEqualTo(1.0);
        assertThat(count("backfill", "requeue")).isEqualTo(1.0);
        assertThat(count("backfill", "success")).isZero();
    }

    private double count(String rank, String result) {
        var counter = meters.find("lol.match.process").tags("rank", rank, "result", result).counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    @DisplayName("허가가 거절되면 Riot 을 부르지 않고 DENIED 를 돌려주고 처리 표시를 푼다")
    void deniedReturnsDeniedAndReleasesProcessing() {
        RiotPermitFixtures.denyAll(permitPort);

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.BACKFILL)).isEqualTo(MatchDataProcessor.Result.DENIED);
        verify(redis, times(1)).releaseProcessing("KR_1");
        assertThat(seen).isEmpty();
        assertThat(count("backfill", "denied")).isEqualTo(1.0);
        assertThat(count("backfill", "requeue")).isZero();
    }

    @Test
    @DisplayName("허가를 묻다 예외가 나면 Riot 을 부르지 않고 FAILURE 이고 처리 표시를 푼다")
    void permitCheckThrows_failureAndReleasesMark() {
        when(permitPort.tryAcquire(anyString(), any(), anyMap())).thenThrow(new IllegalStateException("redis down"));

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.BACKFILL)).isEqualTo(MatchDataProcessor.Result.FAILURE);
        verify(redis, times(1)).releaseProcessing("KR_1");
        assertThat(seen).isEmpty();
    }

    @Test
    @DisplayName("fetch 가 시간 안에 끝나지 않으면 일시 오류로 보고 RETRY 이며 표시를 풀고 호출을 취소한다")
    void fetchTimeout_retriesReleasesMarkAndCancels() {
        matchResult = new CompletableFuture<>();
        timelineResult = new CompletableFuture<>();
        processor = new MatchDataProcessor(port, permitPort, redis, batch, messageSender, Runnable::run,
                matchRepository, meters, Duration.ofMillis(50));

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.BACKFILL)).isEqualTo(MatchDataProcessor.Result.RETRY);
        verify(redis, times(1)).releaseProcessing("KR_1");
        assertThat(matchResult).isCancelled();
        assertThat(timelineResult).isCancelled();
        assertThat(count("backfill", "retry")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("배치 큐가 가득 차 있으면 허가·조회 없이 QUEUE_FULL 이고 처리 표시를 잡지 않는다")
    void batchFullBeforeFetch_queueFullWithoutPermitOrRiotCall() {
        when(batch.hasCapacity()).thenReturn(false);

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.USER_RETRY))
                .isEqualTo(MatchDataProcessor.Result.QUEUE_FULL);
        verifyNoInteractions(permitPort, redis);
        assertThat(seen).isEmpty();
        verify(batch, never()).add(any(), any());
        assertThat(count("user_retry", "requeue")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("받은 뒤 배치 큐에 넣지 못하면 QUEUE_FULL 이고 처리 표시를 푼다")
    void batchQueueFull_releasesMark() {
        when(batch.add(any(), any())).thenReturn(false);

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.BACKFILL)).isEqualTo(MatchDataProcessor.Result.QUEUE_FULL);
        verify(redis, times(1)).releaseProcessing("KR_1");
    }

    @Test
    @DisplayName("match 가 4xx(429 제외)면 FAILURE 이고 processing 표시를 푼다")
    void process_clientError_failureAndReleasesProcessing() {
        matchResult = CompletableFuture.failedFuture(new RiotClientException(HttpStatus.BAD_REQUEST, "x", null));

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.BACKFILL))
                .isEqualTo(MatchDataProcessor.Result.FAILURE);
        verify(redis, times(1)).releaseProcessing("KR_1");
        verify(batch, never()).add(any(), any());
        assertThat(count("backfill", "failure")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("match 가 429·5xx·IO 로 실패하면 RETRY 이고 processing 표시를 푼다")
    void process_transientMatchFailure_retryAndReleasesProcessing() {
        List<Throwable> failures = List.of(rateLimit(), new RiotServerException(HttpStatus.BAD_GATEWAY, "x"),
                new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out")));
        for (Throwable failure : failures) {
            matchResult = CompletableFuture.failedFuture(failure);

            assertThat(processor.process("KR_1", "KR", MatchQueueRank.USER_RETRY))
                    .isEqualTo(MatchDataProcessor.Result.RETRY);
        }
        verify(redis, times(3)).releaseProcessing("KR_1");
        verify(batch, never()).add(any(), any());
        verifyNoInteractions(messageSender);
        assertThat(count("user_retry", "retry")).isEqualTo(3.0);
    }

    @Test
    @DisplayName("match 는 받고 timeline 만 실패하면 매치를 저장하고 그 id 를 나중 타임라인으로 넘긴 뒤 SUCCESS 다")
    void process_timelineOnlyFailure_savesMatchAndSendsTimelineLater() {
        MatchDto match = new MatchDto();
        matchResult = CompletableFuture.completedFuture(match);
        timelineResult = CompletableFuture.failedFuture(new RiotServerException(HttpStatus.BAD_GATEWAY, "x"));

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.BACKFILL))
                .isEqualTo(MatchDataProcessor.Result.SUCCESS);
        verify(batch).add(match, null);
        verify(messageSender).sendMessageByMatchId("KR_1", "KR", MatchQueueRank.TIMELINE_LATER);
        verify(redis, never()).releaseProcessing(anyString());
    }

    @Test
    @DisplayName("나중 타임라인 발행이 실패해도 매치는 저장했으니 SUCCESS 다")
    void process_timelineLaterPublishFails_stillSuccess() {
        MatchDto match = new MatchDto();
        matchResult = CompletableFuture.completedFuture(match);
        timelineResult = CompletableFuture.failedFuture(rateLimit());
        doThrow(new IllegalStateException("mq down"))
                .when(messageSender).sendMessageByMatchId("KR_1", "KR", MatchQueueRank.TIMELINE_LATER);

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.BACKFILL))
                .isEqualTo(MatchDataProcessor.Result.SUCCESS);
        verify(batch).add(match, null);
    }

    @Test
    @DisplayName("나중 타임라인: 타임라인이 이미 있으면 Riot 호출 없이 DUPLICATE 이다")
    void timelineLater_skipsWhenTimelineExists() {
        when(matchRepository.findMatchIdsWithTimeline(List.of("KR_1"))).thenReturn(Set.of("KR_1"));

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.TIMELINE_LATER))
                .isEqualTo(MatchDataProcessor.Result.DUPLICATE);
        assertThat(seen).isEmpty();
        verifyNoInteractions(permitPort);
        verify(batch, never()).add(any(), any());
    }

    @Test
    @DisplayName("나중 타임라인: DEFERRED 로 타임라인 허가 1건만 받아 타임라인만 적재한다")
    void timelineLater_fetchesTimelineOnlyWithDeferredPermit() {
        TimelineDto timeline = timelineOf("KR_1");
        timelineResult = CompletableFuture.completedFuture(timeline);

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.TIMELINE_LATER))
                .isEqualTo(MatchDataProcessor.Result.SUCCESS);

        verify(permitPort).tryAcquire("KR", RiotCallPriority.DEFERRED, Map.of(RiotMethod.MATCH_TIMELINE, 1));
        assertThat(seen).hasSize(1);
        verify(batch).add(null, timeline);
        verify(matchRepository, never()).findExistingMatchIds(any());
        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("나중 타임라인: 본문이 비었거나 metadata·matchId 가 없으면 저장하지 않고 FAILURE 다")
    void timelineLater_emptyBodyOrMissingMatchId_failure() {
        TimelineDto noMatchId = timelineOf(null);
        List<TimelineDto> bodies = new ArrayList<>();
        bodies.add(null);
        bodies.add(new TimelineDto());
        bodies.add(noMatchId);
        for (TimelineDto body : bodies) {
            timelineResult = CompletableFuture.completedFuture(body);

            assertThat(processor.process("KR_1", "KR", MatchQueueRank.TIMELINE_LATER))
                    .isEqualTo(MatchDataProcessor.Result.FAILURE);
        }
        verify(batch, never()).add(any(), any());
        assertThat(count("timeline_later", "failure")).isEqualTo(3.0);
    }

    @Test
    @DisplayName("match 본문이 비면 저장하지 않고 FAILURE 이며 처리 표시를 푼다")
    void process_emptyMatchBody_failureAndReleasesMark() {
        matchResult = CompletableFuture.completedFuture(null);

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.BACKFILL))
                .isEqualTo(MatchDataProcessor.Result.FAILURE);
        verify(batch, never()).add(any(), any());
        verify(redis, times(1)).releaseProcessing("KR_1");
        verifyNoInteractions(messageSender);
    }

    @Test
    @DisplayName("timeline 본문만 비면 받지 못한 것으로 보고 매치를 저장한 뒤 나중 타임라인으로 넘긴다")
    void process_emptyTimelineBody_savesMatchAndSendsTimelineLater() {
        MatchDto match = new MatchDto();
        matchResult = CompletableFuture.completedFuture(match);
        timelineResult = CompletableFuture.completedFuture(null);

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.BACKFILL))
                .isEqualTo(MatchDataProcessor.Result.SUCCESS);
        verify(batch).add(match, null);
        verify(messageSender).sendMessageByMatchId("KR_1", "KR", MatchQueueRank.TIMELINE_LATER);
    }

    @Test
    @DisplayName("나중 타임라인: 429·5xx·IO 로 실패하면 RETRY 다")
    void timelineLater_transientFailure_retry() {
        timelineResult = CompletableFuture.failedFuture(new RiotServerException(HttpStatus.SERVICE_UNAVAILABLE, "x"));

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.TIMELINE_LATER))
                .isEqualTo(MatchDataProcessor.Result.RETRY);
        verify(batch, never()).add(any(), any());
        assertThat(count("timeline_later", "retry")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("나중 타임라인: 4xx(429 제외)면 FAILURE 다")
    void timelineLater_clientError_failure() {
        timelineResult = CompletableFuture.failedFuture(new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "x", null));

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.TIMELINE_LATER))
                .isEqualTo(MatchDataProcessor.Result.FAILURE);
        verify(batch, never()).add(any(), any());
    }

    @Test
    @DisplayName("나중 타임라인: 시간 안에 끝나지 않으면 RETRY 이고 호출을 취소한다")
    void timelineLater_timeout_retryAndCancels() {
        timelineResult = new CompletableFuture<>();
        processor = new MatchDataProcessor(port, permitPort, redis, batch, messageSender, Runnable::run,
                matchRepository, meters, Duration.ofMillis(50));

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.TIMELINE_LATER))
                .isEqualTo(MatchDataProcessor.Result.RETRY);
        assertThat(timelineResult).isCancelled();
    }

    @Test
    @DisplayName("나중 타임라인: 배치 큐가 가득 차 있으면 허가·조회 없이 QUEUE_FULL 이다")
    void timelineLater_batchFull_queueFullWithoutPermit() {
        when(batch.hasCapacity()).thenReturn(false);

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.TIMELINE_LATER))
                .isEqualTo(MatchDataProcessor.Result.QUEUE_FULL);
        verifyNoInteractions(permitPort);
        assertThat(seen).isEmpty();
    }

    @Test
    @DisplayName("나중 타임라인: 허가가 거절되면 DENIED 이고 Riot 을 부르지 않는다")
    void timelineLater_deniedReturnsDenied() {
        RiotPermitFixtures.denyAll(permitPort);

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.TIMELINE_LATER))
                .isEqualTo(MatchDataProcessor.Result.DENIED);
        assertThat(seen).isEmpty();
        verify(batch, never()).add(any(), any());
    }

    @Test
    @DisplayName("성공 시 표시를 유지한다")
    void success_keepsMark() {
        assertThat(processor.process("KR_1", "KR", MatchQueueRank.BACKFILL)).isEqualTo(MatchDataProcessor.Result.SUCCESS);
        verify(redis, never()).releaseProcessing(anyString());
    }
}
