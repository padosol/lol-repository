package com.mmrtr.lol.infra.persistence.match.service;

import com.mmrtr.lol.domain.match.readmodel.timeline.FramesTimeLineDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.InfoTimeLineDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.MetadataTimeLineDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.ParticipantFrameDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.ParticipantFramesDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.infra.persistence.match.entity.timeline.ParticipantFrameEntity;
import com.mmrtr.lol.infra.persistence.match.repository.TimeLineRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TimeLineServiceTest {

    private TimeLineRepositoryImpl repository;
    private TimeLineService service;

    @BeforeEach
    void setUp() {
        repository = mock(TimeLineRepositoryImpl.class);
        service = new TimeLineService(repository);
    }

    @Test
    @DisplayName("매치 엔티티 없이 타임라인만 넘겨도 metadata 의 matchId 로 저장한다")
    void savesTimelineWithoutMatchEntityInSameCall() {
        when(repository.findExistingMatchIds(List.of("KR_1"))).thenReturn(Set.of());

        service.saveAll(List.of(timeline("KR_1")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ParticipantFrameEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).bulkSaveParticipantFrames(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getMatchId()).isEqualTo("KR_1");
    }

    @Test
    @DisplayName("participant_frame 에 이미 있는 matchId 는 저장하지 않는다")
    void skipsTimelineAlreadySaved() {
        when(repository.findExistingMatchIds(List.of("KR_1", "KR_2"))).thenReturn(Set.of("KR_1"));

        service.saveAll(List.of(timeline("KR_1"), timeline("KR_2")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ParticipantFrameEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).bulkSaveParticipantFrames(captor.capture());
        assertThat(captor.getValue()).extracting(ParticipantFrameEntity::getMatchId).containsOnly("KR_2");
    }

    @Test
    @DisplayName("전부 이미 있으면 bulk 저장을 부르지 않는다")
    void allAlreadySaved_noBulkSave() {
        when(repository.findExistingMatchIds(List.of("KR_1"))).thenReturn(Set.of("KR_1"));

        service.saveAll(List.of(timeline("KR_1")));

        verify(repository, never()).bulkSaveParticipantFrames(anyList());
    }

    private TimelineDto timeline(String matchId) {
        MetadataTimeLineDto metadata = new MetadataTimeLineDto();
        metadata.setMatchId(matchId);

        ParticipantFrameDto pf = new ParticipantFrameDto();
        pf.setParticipantId(1);
        ParticipantFramesDto pfs = new ParticipantFramesDto();
        pfs.setP1(pf);
        FramesTimeLineDto frame = new FramesTimeLineDto();
        frame.setTimestamp(60000);
        frame.setParticipantFrames(pfs);
        InfoTimeLineDto info = new InfoTimeLineDto();
        info.setFrames(List.of(frame));

        TimelineDto dto = new TimelineDto();
        dto.setMetadata(metadata);
        dto.setInfo(info);
        return dto;
    }
}
