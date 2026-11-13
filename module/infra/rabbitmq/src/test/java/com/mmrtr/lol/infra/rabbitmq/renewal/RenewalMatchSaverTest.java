package com.mmrtr.lol.infra.rabbitmq.renewal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mmrtr.lol.domain.match.application.usecase.SaveMatchDataUseCase;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RenewalMatchSaverTest {

    private final SaveMatchDataUseCase saveMatchDataUseCase = mock(SaveMatchDataUseCase.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final List<Runnable> submitted = new ArrayList<>();
    private final Executor capturing = submitted::add;

    private final List<MatchDto> matches = List.of(new MatchDto());
    private final List<TimelineDto> timelines = List.of(new TimelineDto());

    private double count(String result) {
        return meterRegistry.timer(RenewalMatchSaver.METRIC, "result", result).count();
    }

    @Test
    @DisplayName("저장은 실행기에서 돌고, 끝나면 success 로 센다")
    void save_runsOnExecutorAndRecordsSuccess() {
        // given
        RenewalMatchSaver saver = new RenewalMatchSaver(saveMatchDataUseCase, capturing, meterRegistry);

        // when
        saver.save(matches, timelines);

        // then
        verify(saveMatchDataUseCase, never()).execute(anyList(), anyList());
        assertThat(submitted).hasSize(1);

        submitted.get(0).run();

        verify(saveMatchDataUseCase).execute(matches, timelines);
        assertThat(count("success")).isEqualTo(1);
        assertThat(count("failure")).isZero();
    }

    @Test
    @DisplayName("저장이 던지면 failure 로 세고 밖으로 던지지 않는다")
    void save_failureIsCountedNotThrown() {
        // given
        willThrow(new IllegalStateException("db down")).given(saveMatchDataUseCase).execute(anyList(), anyList());
        RenewalMatchSaver saver = new RenewalMatchSaver(saveMatchDataUseCase, capturing, meterRegistry);
        saver.save(matches, timelines);

        // when & then
        assertThatCode(() -> submitted.get(0).run()).doesNotThrowAnyException();
        assertThat(count("failure")).isEqualTo(1);
        assertThat(count("success")).isZero();
    }

    @Test
    @DisplayName("실행기가 거부해도 던지지 않고 failure 로 센다")
    void save_rejectedIsCountedNotThrown() {
        // given
        Executor rejecting = task -> {
            throw new RejectedExecutionException("full");
        };
        RenewalMatchSaver saver = new RenewalMatchSaver(saveMatchDataUseCase, rejecting, meterRegistry);

        // when & then
        assertThatCode(() -> saver.save(matches, timelines)).doesNotThrowAnyException();
        verify(saveMatchDataUseCase, never()).execute(anyList(), anyList());
        assertThat(count("failure")).isEqualTo(1);
    }

    @Test
    @DisplayName("저장할 것이 없으면 실행기에 넘기지 않는다")
    void save_nothingToSave_skips() {
        // given
        RenewalMatchSaver saver = new RenewalMatchSaver(saveMatchDataUseCase, capturing, meterRegistry);

        // when
        saver.save(List.of(), List.of());

        // then
        assertThat(submitted).isEmpty();
    }
}
