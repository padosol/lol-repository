package com.mmrtr.lol.infra.persistence.match.service;

import com.mmrtr.lol.domain.match.readmodel.ChallengesDto;
import com.mmrtr.lol.domain.match.readmodel.InfoDto;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.MetadataDto;
import com.mmrtr.lol.domain.match.readmodel.ObjectivesDto;
import com.mmrtr.lol.domain.match.readmodel.ParticipantDto;
import com.mmrtr.lol.domain.match.readmodel.TeamDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.infra.persistence.match.repository.ChallengesRepositoryImpl;
import com.mmrtr.lol.infra.persistence.match.repository.MatchBanRepositoryImpl;
import com.mmrtr.lol.infra.persistence.match.repository.MatchRepositoryImpl;
import com.mmrtr.lol.infra.persistence.match.repository.MatchSummonerRepositoryImpl;
import com.mmrtr.lol.infra.persistence.match.repository.MatchTeamRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchServiceSaveOrderTest {

    @Mock
    private MatchRepositoryImpl matchRepository;
    @Mock
    private MatchSummonerRepositoryImpl matchSummonerRepository;
    @Mock
    private ChallengesRepositoryImpl challengesRepository;
    @Mock
    private MatchTeamRepositoryImpl matchTeamRepository;
    @Mock
    private MatchBanRepositoryImpl matchBanRepository;
    @Mock
    private TimeLineService timeLineService;
    @Mock
    private TimelineEventJsonService timelineEventJsonService;

    private MatchService service;

    @BeforeEach
    void setUp() {
        service = new MatchService(matchRepository, matchSummonerRepository, challengesRepository,
                matchTeamRepository, matchBanRepository, timeLineService, timelineEventJsonService,
                Runnable::run);
    }

    @Test
    @DisplayName("match 행은 자식 테이블·타임라인 저장이 끝난 뒤 마지막에 저장한다")
    void saveAll_savesMatchRowLast() {
        // when
        service.saveAll(List.of(matchDto()), List.of(new TimelineDto()), Map.of());

        // then
        InOrder order = inOrder(matchSummonerRepository, timeLineService,
                timelineEventJsonService, matchRepository);
        order.verify(matchSummonerRepository).bulkSave(anyList());
        order.verify(timeLineService).saveAll(anyList(), anyList());
        order.verify(timelineEventJsonService).saveAll(anyList());
        order.verify(matchRepository).bulkSave(anyList());
    }

    @Test
    @DisplayName("자식 저장이 실패하면 match 행을 저장하지 않는다 — 다음 처리가 다시 저장한다")
    void saveAll_childSaveFails_doesNotSaveMatchRow() {
        // given
        doThrow(new IllegalStateException("boom")).when(timeLineService).saveAll(anyList(), anyList());
        List<MatchDto> matches = List.of(matchDto());
        List<TimelineDto> timelines = List.of(new TimelineDto());

        // when & then
        assertThatThrownBy(() -> service.saveAll(matches, timelines, Map.of()))
                .isInstanceOf(CompletionException.class);
        verify(matchRepository, never()).bulkSave(anyList());
    }

    @Test
    @DisplayName("saveAll 에는 바깥 트랜잭션이 없다 — 기다리는 동안 커넥션을 쥐지 않는다")
    void saveAll_hasNoOuterTransaction() throws Exception {
        // when
        Method m = MatchService.class.getMethod("saveAll", List.class, List.class, Map.class);

        // then
        assertThat(m.getAnnotation(Transactional.class)).isNull();
    }

    private MatchDto matchDto() {
        MetadataDto metadata = new MetadataDto();
        metadata.setMatchId("KR_1");
        metadata.setDataVersion("2");

        ParticipantDto participant = new ParticipantDto();
        participant.setPuuid("puuid-1");
        participant.setChallenges(new ChallengesDto());

        TeamDto team = new TeamDto();
        team.setTeamId(100);
        team.setObjectives(new ObjectivesDto());

        InfoDto info = new InfoDto();
        info.setGameVersion("14.23.1");
        info.setQueueId(420);
        info.setParticipants(List.of(participant));
        info.setTeams(List.of(team));

        MatchDto dto = new MatchDto();
        dto.setMetadata(metadata);
        dto.setInfo(info);
        return dto;
    }
}
