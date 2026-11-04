package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.domain.match.application.port.MatchRepositoryPort;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.infra.redis.service.MatchRedisService;
import com.mmrtr.lol.infra.riot.exception.RiotBudgetExceededException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotCallScope;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchDataProcessorTest {

    private final List<RiotCallPriority> seen = new ArrayList<>();
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
        when(batch.add(any())).thenReturn(true);
        matchResult = CompletableFuture.completedFuture(new MatchDto());
        timelineResult = CompletableFuture.completedFuture(new TimelineDto());

        port = new MatchApiPort() {
            @Override
            public CompletableFuture<List<String>> fetchMatchIdsByPuuid(String p, String pl, Executor e) {
                return CompletableFuture.completedFuture(List.of());
            }

            @Override
            public CompletableFuture<List<String>> fetchMatchIdsByPuuid(
                    String p, String pl, long s, int st, int c, Executor e) {
                return CompletableFuture.completedFuture(List.of());
            }

            @Override
            public CompletableFuture<MatchDto> fetchMatchById(String id, String pl, Executor e) {
                seen.add(RiotCallScope.current());
                return matchResult;
            }

            @Override
            public CompletableFuture<TimelineDto> fetchTimelineById(String id, String pl, Executor e) {
                seen.add(RiotCallScope.current());
                return timelineResult;
            }
        };
        processor = new MatchDataProcessor(port, redis, batch, Runnable::run, matchRepository, meters);
    }

    @Test
    @DisplayName("백필 순위는 매치·타임라인 호출을 BACKGROUND 스코프에서 만든다")
    void process_backfill_callsWithBackgroundPriority() {
        processor.process("KR_1", "KR", MatchQueueRank.BACKFILL);

        assertThat(seen).containsExactly(RiotCallPriority.BACKGROUND, RiotCallPriority.BACKGROUND);
        assertThat(RiotCallScope.current()).isEqualTo(RiotCallPriority.USER);
    }

    @Test
    @DisplayName("사용자 재시도 순위는 USER 스코프에서 호출을 만든다")
    void process_userRetry_callsWithUserPriority() {
        processor.process("KR_1", "KR", MatchQueueRank.USER_RETRY);

        assertThat(seen).containsExactly(RiotCallPriority.USER, RiotCallPriority.USER);
    }

    @Test
    @DisplayName("이미 DB 에 있으면 Riot 호출 없이 DUPLICATE 이다")
    void process_alreadyInDb_duplicateWithoutRiotCall() {
        when(matchRepository.findExistingMatchIds(List.of("KR_1"))).thenReturn(List.of("KR_1"));

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.USER_RETRY))
                .isEqualTo(MatchDataProcessor.Result.DUPLICATE);
        assertThat(seen).isEmpty();
        verifyNoInteractions(redis);
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
        when(batch.add(any())).thenReturn(false);
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
    @DisplayName("예산 초과면 QUEUE_FULL 로 requeue 하고 처리 표시를 푼다")
    void budgetExceeded_requeuesAndReleasesMark() {
        matchResult = CompletableFuture.failedFuture(
                new RiotBudgetExceededException("asia", RiotCallPriority.BACKGROUND));

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.BACKFILL)).isEqualTo(MatchDataProcessor.Result.QUEUE_FULL);
        verify(redis, times(1)).releaseProcessing("KR_1");
    }

    @Test
    @DisplayName("fetch 가 시간 안에 끝나지 않으면(한도 대기 등) QUEUE_FULL 로 requeue 하고 표시를 풀고 호출을 취소한다")
    void fetchTimeout_requeuesReleasesMarkAndCancels() {
        matchResult = new CompletableFuture<>();
        timelineResult = new CompletableFuture<>();
        processor = new MatchDataProcessor(port, redis, batch, Runnable::run, matchRepository, meters, Duration.ofMillis(50));

        assertThat(processor.process("KR_1", "KR", MatchQueueRank.BACKFILL)).isEqualTo(MatchDataProcessor.Result.QUEUE_FULL);
        verify(redis, times(1)).releaseProcessing("KR_1");
        assertThat(matchResult).isCancelled();
        assertThat(timelineResult).isCancelled();
    }

    @Test
    @DisplayName("배치 큐가 가득 차면 QUEUE_FULL 이고 처리 표시를 푼다")
    void batchQueueFull_releasesMark() {
        when(batch.add(any())).thenReturn(false);

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
    @DisplayName("성공 시 표시를 유지한다")
    void success_keepsMark() {
        assertThat(processor.process("KR_1", "KR", MatchQueueRank.BACKFILL)).isEqualTo(MatchDataProcessor.Result.SUCCESS);
        verify(redis, never()).releaseProcessing(anyString());
    }
}
