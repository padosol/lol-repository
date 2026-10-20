package com.mmrtr.lol.domain.match.application;

import com.mmrtr.lol.domain.match.application.port.MatchQueryPort;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.GameInfo;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.ParticipantRecord;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.Team;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.TeamObjectives;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.Event;
import com.mmrtr.lol.domain.match.readmodel.ParticipantReadModel;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchQueryServiceTest {

    private static final String MATCH_ID = "KR_1";
    private static final GameInfo GAME_INFO = new GameInfo("2", 1L, 1800L, 2L, "CLASSIC", 1L, "MATCHED_GAME",
            "15.1.1", 11, "KR", 420, null, MATCH_ID, "GOLD", "IV");

    @Mock
    private MatchQueryPort matchQueryPort;

    @InjectMocks
    private MatchQueryService service;

    @DisplayName("저장되지 않은 매치는 NOT_FOUND_MATCH_ID — 참가자·타임라인을 읽지 않는다")
    @Test
    void getGame_unknownMatch_throwsNotFoundMatchId() {
        // given
        given(matchQueryPort.findGameInfo(MATCH_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getGame(MATCH_ID))
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND_MATCH_ID));
        verify(matchQueryPort, never()).findParticipants(any());
    }

    @DisplayName("저장된 매치는 그 매치의 참가자·타임라인으로 조립한다")
    @Test
    void getGame_storedMatch_assemblesParticipantsAndTimeline() {
        // given
        ParticipantReadModel participant = ParticipantReadModel.builder().participantId(1).teamId(100).win(true).build();
        given(matchQueryPort.findGameInfo(MATCH_ID)).willReturn(Optional.of(GAME_INFO));
        given(matchQueryPort.findParticipants(List.of(MATCH_ID))).willReturn(List.of(new ParticipantRecord(
                MATCH_ID, participant, new TeamObjectives(10, 0, 1, 2, 0), null, null)));
        given(matchQueryPort.findTimelineEvents(List.of(MATCH_ID)))
                .willReturn(List.of(new Event(MATCH_ID, 1, "ITEM_PURCHASED", 1055, null, 60000)));

        // when
        GameReadModel game = service.getGame(MATCH_ID);

        // then
        assertThat(game.gameInfoData()).isEqualTo(GAME_INFO);
        assertThat(game.participantData().get(0).itemSeq()).hasSize(1);
        assertThat(game.teamInfoData().blueTeam().championKills()).isEqualTo(10);
    }

    @DisplayName("참가자가 아직 없는 매치도 빈 팀 정보를 둔다 (lol-server 단건 조회와 같다)")
    @Test
    void getGame_noParticipants_returnsEmptyTeam() {
        // given
        given(matchQueryPort.findGameInfo(MATCH_ID)).willReturn(Optional.of(GAME_INFO));
        given(matchQueryPort.findParticipants(List.of(MATCH_ID))).willReturn(List.of());
        given(matchQueryPort.findTimelineEvents(List.of(MATCH_ID))).willReturn(List.of());

        // when
        GameReadModel game = service.getGame(MATCH_ID);

        // then
        assertThat(game.teamInfoData()).isEqualTo(new Team(null, null));
        assertThat(game.participantData()).isEmpty();
    }

    @DisplayName("타임라인은 저장된 매치인지 보지 않고 이벤트만으로 만든다 — 없으면 빈 응답")
    @Test
    void getTimeline_noEvents_returnsEmptyWithoutLookingUpMatch() {
        // given
        given(matchQueryPort.findTimelineEvents(List.of("ghost"))).willReturn(List.of());

        // when & then
        assertThat(service.getTimeline("ghost").participants()).isEmpty();
        verify(matchQueryPort, never()).findGameInfo(any());
    }
}
