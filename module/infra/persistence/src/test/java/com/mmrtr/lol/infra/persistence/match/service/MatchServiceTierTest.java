package com.mmrtr.lol.infra.persistence.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.mmrtr.lol.domain.match.domain.MatchTierSnapshot;
import com.mmrtr.lol.domain.match.domain.MatchTierSnapshot.ParticipantTier;
import com.mmrtr.lol.domain.match.readmodel.ChallengesDto;
import com.mmrtr.lol.domain.match.readmodel.InfoDto;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.MetadataDto;
import com.mmrtr.lol.domain.match.readmodel.ObjectivesDto;
import com.mmrtr.lol.domain.match.readmodel.ParticipantDto;
import com.mmrtr.lol.domain.match.readmodel.TeamDto;
import com.mmrtr.lol.infra.persistence.match.entity.MatchEntity;
import com.mmrtr.lol.infra.persistence.match.entity.MatchParticipantEntity;
import com.mmrtr.lol.infra.persistence.match.repository.ChallengesRepositoryImpl;
import com.mmrtr.lol.infra.persistence.match.repository.MatchBanRepositoryImpl;
import com.mmrtr.lol.infra.persistence.match.repository.MatchRepositoryImpl;
import com.mmrtr.lol.infra.persistence.match.repository.MatchSummonerRepositoryImpl;
import com.mmrtr.lol.infra.persistence.match.repository.MatchTeamRepositoryImpl;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchServiceTierTest {

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
    @Captor
    private ArgumentCaptor<List<MatchParticipantEntity>> participantCaptor;
    @Captor
    private ArgumentCaptor<List<MatchEntity>> matchCaptor;

    private MatchService service;

    @BeforeEach
    void setUp() {
        service = new MatchService(matchRepository, matchSummonerRepository, challengesRepository,
                matchTeamRepository, matchBanRepository, timeLineService, timelineEventJsonService,
                Runnable::run);
    }

    @Test
    void 스냅샷의_티어를_참가자와_매치에_쓴다() {
        MatchTierSnapshot snapshot = new MatchTierSnapshot(
                Map.of("p1", new ParticipantTier("GOLD", "II", 100)), 100);

        service.saveAll(List.of(matchDto(true)), List.of(), Map.of("KR_1", snapshot));

        verify(matchSummonerRepository).bulkSave(participantCaptor.capture());
        MatchParticipantEntity saved = participantCaptor.getValue().stream()
                .filter(p -> "p1".equals(p.getPuuid())).findFirst().orElseThrow();
        assertThat(saved.getTier()).isEqualTo("GOLD");
        assertThat(saved.getTierRank()).isEqualTo("II");
        assertThat(saved.getAbsolutePoints()).isEqualTo(100);
        verify(matchRepository).bulkSave(matchCaptor.capture());
        assertThat(matchCaptor.getValue().get(0).getAverageTier()).isEqualTo(100);
    }

    @Test
    void challenges가_없는_참가자는_평균에만_들어간다() {
        MatchTierSnapshot snapshot = new MatchTierSnapshot(
                Map.of("p1", new ParticipantTier("GOLD", "II", 100),
                        "p2", new ParticipantTier("GOLD", "I", 200)), 150);

        service.saveAll(List.of(matchDto(false)), List.of(), Map.of("KR_1", snapshot));

        verify(matchSummonerRepository).bulkSave(participantCaptor.capture());
        assertThat(participantCaptor.getValue()).extracting(MatchParticipantEntity::getPuuid)
                .containsExactly("p1");
        verify(matchRepository).bulkSave(matchCaptor.capture());
        assertThat(matchCaptor.getValue().get(0).getAverageTier()).isEqualTo(150);
    }

    private MatchDto matchDto(boolean p2HasChallenges) {
        MetadataDto metadata = new MetadataDto();
        metadata.setMatchId("KR_1");
        metadata.setDataVersion("2");

        TeamDto team = new TeamDto();
        team.setTeamId(100);
        team.setObjectives(new ObjectivesDto());

        InfoDto info = new InfoDto();
        info.setGameVersion("14.23.1");
        info.setQueueId(420);
        info.setParticipants(List.of(participant("p1", true), participant("p2", p2HasChallenges)));
        info.setTeams(List.of(team));

        MatchDto dto = new MatchDto();
        dto.setMetadata(metadata);
        dto.setInfo(info);
        return dto;
    }

    private ParticipantDto participant(String puuid, boolean withChallenges) {
        ParticipantDto participant = new ParticipantDto();
        participant.setPuuid(puuid);
        if (withChallenges) {
            participant.setChallenges(new ChallengesDto());
        }
        return participant;
    }
}
