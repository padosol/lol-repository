package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.application.usecase.SaveMatchDataUseCase;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AsyncMatchSaverTest {

    private SaveMatchDataUseCase saveMatchDataUseCase;
    private AsyncMatchSaver asyncMatchSaver;

    @BeforeEach
    void setUp() {
        saveMatchDataUseCase = mock(SaveMatchDataUseCase.class);
        Executor sameThreadExecutor = Runnable::run;
        asyncMatchSaver = new AsyncMatchSaver(saveMatchDataUseCase, sameThreadExecutor);
    }

    @Test
    @DisplayName("matches 가 null 이면 SaveMatchDataUseCase 호출 안 함")
    void saveAsync_skipsWhenMatchesNull() {
        asyncMatchSaver.saveAsync(null, List.of());

        verifyNoInteractions(saveMatchDataUseCase);
    }

    @Test
    @DisplayName("matches 가 빈 리스트면 SaveMatchDataUseCase 호출 안 함")
    void saveAsync_skipsWhenMatchesEmpty() {
        asyncMatchSaver.saveAsync(Collections.emptyList(), List.of());

        verifyNoInteractions(saveMatchDataUseCase);
    }

    @Test
    @DisplayName("정상 흐름: SaveMatchDataUseCase.execute 에 동일 인자로 위임한다")
    void saveAsync_delegatesToUseCase() {
        MatchDto match = new MatchDto();
        TimelineDto timeline = new TimelineDto();
        List<MatchDto> matches = List.of(match);
        List<TimelineDto> timelines = List.of(timeline);

        asyncMatchSaver.saveAsync(matches, timelines);

        verify(saveMatchDataUseCase, times(1)).execute(matches, timelines);
    }

    @Test
    @DisplayName("SaveMatchDataUseCase 가 예외 던져도 호출자에게 전파하지 않는다 (swallow)")
    void saveAsync_swallowsException() {
        MatchDto match = new MatchDto();
        List<MatchDto> matches = List.of(match);
        List<TimelineDto> timelines = List.of();
        doThrow(new RuntimeException("DB down")).when(saveMatchDataUseCase).execute(matches, timelines);

        asyncMatchSaver.saveAsync(matches, timelines);

        verify(saveMatchDataUseCase, times(1)).execute(matches, timelines);
    }
}
