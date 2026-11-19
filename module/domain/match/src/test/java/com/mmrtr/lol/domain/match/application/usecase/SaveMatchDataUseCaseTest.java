package com.mmrtr.lol.domain.match.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mmrtr.lol.domain.league.application.port.LeagueSummonerRepositoryPort;
import com.mmrtr.lol.domain.league.domain.LeagueSummoner;
import com.mmrtr.lol.domain.match.application.port.MatchRepositoryPort;
import com.mmrtr.lol.domain.match.domain.MatchTierSnapshot;
import com.mmrtr.lol.domain.match.readmodel.InfoDto;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.MetadataDto;
import com.mmrtr.lol.domain.match.readmodel.ParticipantDto;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SaveMatchDataUseCaseTest {

    @Mock
    private MatchRepositoryPort matchRepositoryPort;
    @Mock
    private LeagueSummonerRepositoryPort leagueSummonerRepositoryPort;
    @InjectMocks
    private SaveMatchDataUseCase useCase;

    @Test
    void 큐별_리그_정보로_매치마다_스냅샷을_만든다() {
        LeagueSummoner p1 = LeagueSummoner.builder()
                .puuid("p1").tier("GOLD").rank("II").absolutePoints(100).build();
        given(leagueSummonerRepositoryPort.findAllByPuuidsAndQueue(Set.of("p1", "p2"), "RANKED_SOLO_5x5"))
                .willReturn(List.of(p1));

        useCase.execute(List.of(match(420)), List.of());

        Map<String, MatchTierSnapshot> snapshots = captureSnapshots();
        assertThat(snapshots.get("KR_1").averageTier()).isEqualTo(100);
        assertThat(snapshots.get("KR_1").tierOf("p2")).isEmpty();
    }

    @Test
    void 큐를_알_수_없는_매치는_빈_스냅샷을_넘긴다() {
        useCase.execute(List.of(match(9999)), List.of());

        assertThat(captureSnapshots().get("KR_1").averageTier()).isNull();
        verify(leagueSummonerRepositoryPort, never()).findAllByPuuidsAndQueue(any(), anyString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, MatchTierSnapshot> captureSnapshots() {
        ArgumentCaptor<Map<String, MatchTierSnapshot>> captor = ArgumentCaptor.forClass(Map.class);
        verify(matchRepositoryPort).saveAll(any(), any(), captor.capture());
        return captor.getValue();
    }

    private MatchDto match(int queueId) {
        MetadataDto metadata = new MetadataDto();
        metadata.setMatchId("KR_1");
        InfoDto info = new InfoDto();
        info.setQueueId(queueId);
        info.setParticipants(List.of(participant("p1"), participant("p2")));
        MatchDto dto = new MatchDto();
        dto.setMetadata(metadata);
        dto.setInfo(info);
        return dto;
    }

    private ParticipantDto participant(String puuid) {
        ParticipantDto participant = new ParticipantDto();
        participant.setPuuid(puuid);
        return participant;
    }
}
