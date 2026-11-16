package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.application.usecase.SaveMatchDataUseCase;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.util.Pair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MatchBatchProcessorTest {

    private SaveMatchDataUseCase saveMatchDataUseCase;
    private MatchBatchProcessor processor;

    @BeforeEach
    void setUp() {
        saveMatchDataUseCase = mock(SaveMatchDataUseCase.class);
        processor = new MatchBatchProcessor(saveMatchDataUseCase);
    }

    @Test
    @DisplayName("flush 시 drain 된 모든 매치/타임라인이 saveMatchDataUseCase 로 전달된다")
    void flush_passesAllPairsToSaveUseCase() {
        processor.add(Pair.of(new MatchDto(), new TimelineDto()));
        processor.add(Pair.of(new MatchDto(), new TimelineDto()));

        processor.flush();

        verify(saveMatchDataUseCase, times(1)).execute(any(), any());
    }

    @Test
    @DisplayName("큐가 비어있으면 save 가 호출되지 않는다")
    void flush_emptyQueue_doesNothing() {
        processor.flush();

        verify(saveMatchDataUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("큐가 가득 차면 add 는 false 를 반환한다 (backpressure)")
    void add_returnsFalse_whenQueueIsFull() {
        // 큐 용량 500 — 채워 넣은 뒤 추가 add 가 거절되는지 확인
        for (int i = 0; i < 500; i++) {
            boolean accepted = processor.add(Pair.of(new MatchDto(), new TimelineDto()));
            assertThat(accepted).isTrue();
        }

        boolean overflow = processor.add(Pair.of(new MatchDto(), new TimelineDto()));
        assertThat(overflow).isFalse();
    }
}
