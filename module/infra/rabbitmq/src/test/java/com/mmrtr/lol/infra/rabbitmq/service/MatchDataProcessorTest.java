package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.domain.match.application.port.MatchRepositoryPort;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.infra.rabbitmq.RiotPermitFixtures;
import com.mmrtr.lol.infra.redis.service.MatchRedisService;
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
        matchRepository = mock(MatchRepositoryPort.class);
        meters = new SimpleMeterRegistry();
        when(redis.tryMarkProcessing(anyString())).thenReturn(true);
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
        processor = new MatchDataProcessor(port, permitPort, redis, batch, Runnable::run, matchRepository, meters);
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
    @DisplayName("fetch 가 시간 안에 끝나지 않으면 QUEUE_FULL 로 requeue 하고 표시를 풀고 호출을 취소한다")
    void fetchTimeout_requeuesReleasesMarkAndCancels() {
        matchResult = new CompletableFuture<>();
        timelineResult = new CompletableFuture<>();
        processor = new MatchDataProcessor(port, permitPort, redis, batch, Runnable::run, matchRepository, meters,
                Duration.ofMillis(50));

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.BACKFILL)).isEqualTo(MatchDataProcessor.Result.QUEUE_FULL);
        verify(redis, times(1)).releaseProcessing("KR_1");
        assertThat(matchResult).isCancelled();
        assertThat(timelineResult).isCancelled();
    }

    @Test
    @DisplayName("배치 큐가 가득 차면 QUEUE_FULL 이고 처리 표시를 푼다")
    void batchQueueFull_releasesMark() {
        when(batch.add(any(), any())).thenReturn(false);

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.BACKFILL)).isEqualTo(MatchDataProcessor.Result.QUEUE_FULL);
        verify(redis, times(1)).releaseProcessing("KR_1");
    }

    @Test
    @DisplayName("실패해도 processing 표시를 푼다")
    void process_failure_releasesProcessing() {
        matchResult = CompletableFuture.failedFuture(new RiotServerException(HttpStatus.BAD_GATEWAY, "x"));

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.BACKFILL))
                .isEqualTo(MatchDataProcessor.Result.FAILURE);
        verify(redis, times(1)).releaseProcessing("KR_1");
        assertThat(count("backfill", "failure")).isEqualTo(1.0);
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
        TimelineDto timeline = new TimelineDto();
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
