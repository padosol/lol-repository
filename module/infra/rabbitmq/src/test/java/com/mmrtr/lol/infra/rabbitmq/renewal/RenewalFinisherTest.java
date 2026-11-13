package com.mmrtr.lol.infra.rabbitmq.renewal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;
import com.mmrtr.lol.domain.summoner.application.port.SummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.domain.RenewalRecord;
import com.mmrtr.lol.domain.summoner.domain.RenewalStatus;
import com.mmrtr.lol.domain.summoner.domain.RenewalTransition;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RenewalFinisherTest {

    private static final long NOW = 2_000_000L;
    private static final long REVISION = 1_700_000_000_000L;

    private final RenewalRecordPort recordPort = mock(RenewalRecordPort.class);
    private final SummonerRepositoryPort summonerRepositoryPort = mock(SummonerRepositoryPort.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RenewalFinisher finisher =
            new RenewalFinisher(recordPort, summonerRepositoryPort, registry, () -> NOW);

    private RenewalRecord record(RenewalStatus status, String reason, Long revision) {
        return new RenewalRecord("r1", status, reason, 15, NOW - 1200, NOW + 3800, revision, "KR");
    }

    private static LocalDateTime toLocal(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
    }

    private double count(String result, String reason) {
        return registry.counter("lol.renewal.completed", "result", result, "reason", reason).count();
    }

    @Test
    @DisplayName("SUCCEEDED 는 커서와 마지막 호출 시각을 옮기고 성공 지표를 남긴다")
    void finish_succeeded_movesCursorAndLastCall() {
        // given
        given(recordPort.find("p1")).willReturn(Optional.of(record(RenewalStatus.SUCCESS, null, REVISION)));

        // when
        finisher.finish("p1", RenewalTransition.SUCCEEDED);

        // then
        verify(summonerRepositoryPort).updateRevisionDate("p1", toLocal(REVISION));
        verify(summonerRepositoryPort).updateLastRiotCallDate("p1");
        assertThat(count("success", "none")).isEqualTo(1);
        assertThat(registry.timer("lol.renewal.duration", "result", "success").totalTime(TimeUnit.MILLISECONDS))
                .isEqualTo(1200);
    }

    @Test
    @DisplayName("SUCCEEDED 인데 riotRevisionDate 가 없으면 마지막 호출 시각만 옮긴다")
    void finish_succeededWithoutRiotRevision_onlyLastCall() {
        // given
        given(recordPort.find("p1")).willReturn(Optional.of(record(RenewalStatus.SUCCESS, null, null)));

        // when
        finisher.finish("p1", RenewalTransition.SUCCEEDED);

        // then
        verify(summonerRepositoryPort, never()).updateRevisionDate(any(), any());
        verify(summonerRepositoryPort).updateLastRiotCallDate("p1");
    }

    @Test
    @DisplayName("FAILED 는 커서를 두고 이유별로 센다")
    void finish_failed_keepsCursorAndCountsReason() {
        // given
        given(recordPort.find("p1")).willReturn(Optional.of(record(RenewalStatus.FAILED, "deadline", REVISION)));

        // when
        finisher.finish("p1", RenewalTransition.FAILED);

        // then
        verify(summonerRepositoryPort, never()).updateRevisionDate(any(), any());
        verify(summonerRepositoryPort, never()).updateLastRiotCallDate(any());
        assertThat(count("failed", "deadline")).isEqualTo(1);
        assertThat(registry.timer("lol.renewal.duration", "result", "failed").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("LATE_COMPLETED 는 커서만 옮기고 late_completed 를 센다")
    void finish_lateCompleted_movesOnlyCursor() {
        // given
        given(recordPort.find("p1")).willReturn(Optional.of(record(RenewalStatus.FAILED, "deadline", REVISION)));

        // when
        finisher.finish("p1", RenewalTransition.LATE_COMPLETED);

        // then
        verify(summonerRepositoryPort).updateRevisionDate("p1", toLocal(REVISION));
        verify(summonerRepositoryPort, never()).updateLastRiotCallDate(any());
        assertThat(registry.counter("lol.renewal.late_completed").count()).isEqualTo(1);
        assertThat(count("failed", "deadline")).isZero();
    }

    @Test
    @DisplayName("FAILED_LATE_COMPLETED 는 FAILED 와 LATE_COMPLETED 를 모두 한다")
    void finish_failedLateCompleted_doesBoth() {
        // given
        given(recordPort.find("p1")).willReturn(Optional.of(record(RenewalStatus.FAILED, "deadline", REVISION)));

        // when
        finisher.finish("p1", RenewalTransition.FAILED_LATE_COMPLETED);

        // then
        assertThat(count("failed", "deadline")).isEqualTo(1);
        assertThat(registry.counter("lol.renewal.late_completed").count()).isEqualTo(1);
        verify(summonerRepositoryPort).updateRevisionDate("p1", toLocal(REVISION));
        verify(summonerRepositoryPort, never()).updateLastRiotCallDate(any());
    }

    @Test
    @DisplayName("NONE 은 아무것도 하지 않는다")
    void finish_none_doesNothing() {
        // when
        finisher.finish("p1", RenewalTransition.NONE);

        // then
        verifyNoInteractions(summonerRepositoryPort);
        verifyNoInteractions(recordPort);
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    @DisplayName("fail 은 레코드를 FAILED 로 바꾼 전이로 finish 한다")
    void fail_finishesWithRecordTransition() {
        // given
        given(recordPort.fail("p1", "r1", "riot_429")).willReturn(RenewalTransition.FAILED);
        given(recordPort.find("p1")).willReturn(Optional.of(record(RenewalStatus.FAILED, "riot_429", null)));

        // when
        finisher.fail("p1", "r1", "riot_429");

        // then
        assertThat(count("failed", "riot_429")).isEqualTo(1);
    }

    @Test
    @DisplayName("skipped 는 skipped 지표를 센다")
    void skipped_countsSkipped() {
        // when
        finisher.skipped("no_change");

        // then
        assertThat(count("skipped", "no_change")).isEqualTo(1);
    }

    @Test
    @DisplayName("지표에는 priority 태그가 없다")
    void completedCounter_hasNoPriorityTag() {
        // given
        given(recordPort.find("p1")).willReturn(Optional.of(record(RenewalStatus.SUCCESS, null, null)));

        // when
        finisher.finish("p1", RenewalTransition.SUCCEEDED);

        // then
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).extracting(Tag::getKey).doesNotContain("priority"));
    }
}
