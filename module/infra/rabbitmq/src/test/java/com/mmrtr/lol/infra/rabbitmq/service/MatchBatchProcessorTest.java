package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.application.usecase.SaveMatchDataUseCase;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        processor.add(new MatchDto(), new TimelineDto());
        processor.add(new MatchDto(), new TimelineDto());

        processor.flush();

        verify(saveMatchDataUseCase, times(1)).execute(any(), any());
    }

    @Test
    @DisplayName("flush 는 매치 목록과 타임라인 목록으로 한 번만 저장한다 — 타임라인만 있는 항목은 매치 목록에 넣지 않는다")
    void flushPassesMatchesAndTimelineOnlyItemsInOneCall() {
        MatchDto m1 = new MatchDto();
        TimelineDto t1 = new TimelineDto();
        TimelineDto t9 = new TimelineDto();
        processor.add(m1, t1);
        processor.add(null, t9);

        processor.flush();

        verify(saveMatchDataUseCase, times(1)).execute(List.of(m1), List.of(t1, t9));
    }

    @Test
    @DisplayName("타임라인 없는 매치는 매치 목록에만 들어간다")
    void flush_matchWithoutTimeline_onlyInMatches() {
        MatchDto m1 = new MatchDto();
        processor.add(m1, null);

        processor.flush();

        verify(saveMatchDataUseCase).execute(List.of(m1), List.of());
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
            boolean accepted = processor.add(new MatchDto(), new TimelineDto());
            assertThat(accepted).isTrue();
        }

        boolean overflow = processor.add(new MatchDto(), new TimelineDto());
        assertThat(overflow).isFalse();
    }

    @Test
    @DisplayName("hasCapacity 는 큐에 자리가 남았는지 알려 주고, flush 로 비우면 다시 true 다")
    void hasCapacity_reflectsRemainingCapacity() {
        assertThat(processor.hasCapacity()).isTrue();
        for (int i = 0; i < 500; i++) {
            processor.add(new MatchDto(), new TimelineDto());
        }
        assertThat(processor.hasCapacity()).isFalse();

        processor.flush();

        assertThat(processor.hasCapacity()).isTrue();
    }
}
