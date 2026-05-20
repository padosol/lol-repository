package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.application.port.MatchCacheEvictPort;
import com.mmrtr.lol.domain.match.application.usecase.SaveMatchDataUseCase;
import com.mmrtr.lol.domain.match.readmodel.InfoDto;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.ParticipantDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MatchBatchProcessorTest {

    private SaveMatchDataUseCase saveMatchDataUseCase;
    private MatchCacheEvictPort matchCacheEvictPort;
    private MatchBatchProcessor processor;

    @BeforeEach
    void setUp() {
        saveMatchDataUseCase = mock(SaveMatchDataUseCase.class);
        matchCacheEvictPort = mock(MatchCacheEvictPort.class);
        processor = new MatchBatchProcessor(saveMatchDataUseCase, matchCacheEvictPort);
    }

    @Test
    @DisplayName("flush 후 모든 참가자 puuid 가 캐시 evict port 로 전달된다")
    void flush_evictsAllParticipantPuuids() {
        MatchDto m1 = matchWithPuuids(List.of("puuid-a", "puuid-b"));
        MatchDto m2 = matchWithPuuids(List.of("puuid-b", "puuid-c"));
        processor.add(Pair.of(m1, new TimelineDto()));
        processor.add(Pair.of(m2, new TimelineDto()));

        processor.flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> captor = ArgumentCaptor.forClass(Set.class);
        verify(matchCacheEvictPort, times(1)).evictByPuuids(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder("puuid-a", "puuid-b", "puuid-c");
    }

    @Test
    @DisplayName("큐가 비어있으면 save 와 evict 모두 호출되지 않는다")
    void flush_emptyQueue_doesNothing() {
        processor.flush();

        verify(saveMatchDataUseCase, never()).execute(any(), any());
        verify(matchCacheEvictPort, never()).evictByPuuids(any());
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

    private MatchDto matchWithPuuids(List<String> puuids) {
        MatchDto dto = new MatchDto();
        InfoDto info = new InfoDto();
        List<ParticipantDto> participants = new ArrayList<>();
        for (String puuid : puuids) {
            ParticipantDto p = new ParticipantDto();
            p.setPuuid(puuid);
            participants.add(p);
        }
        info.setParticipants(participants);
        dto.setInfo(info);
        return dto;
    }
}
