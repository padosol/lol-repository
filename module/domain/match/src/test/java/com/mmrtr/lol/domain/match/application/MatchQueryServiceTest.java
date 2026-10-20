package com.mmrtr.lol.domain.match.application;

import com.mmrtr.lol.domain.match.application.port.MatchCacheReadPort;
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
import com.mmrtr.lol.support.page.SliceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 조회 규칙과 전적 첫 페이지 캐시 오버레이 — lol-server {@code MatchServiceTest} 의 오버레이 시나리오를 옮겼다. */
class MatchQueryServiceTest {

    private static final String MATCH_ID = "KR_1";
    private static final String PUUID = "puuid-1";

    private final MatchQueryPort matchQueryPort = mock(MatchQueryPort.class);
    private final MatchCacheReadPort matchCacheReadPort = mock(MatchCacheReadPort.class);
    private final MatchQueryService service = new MatchQueryService(matchQueryPort, matchCacheReadPort, Runnable::run);

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
        GameInfo gameInfo = gameInfo(MATCH_ID, 1000L);
        ParticipantReadModel participant = ParticipantReadModel.builder().participantId(1).teamId(100).win(true).build();
        given(matchQueryPort.findGameInfo(MATCH_ID)).willReturn(Optional.of(gameInfo));
        given(matchQueryPort.findParticipants(List.of(MATCH_ID))).willReturn(List.of(new ParticipantRecord(
                MATCH_ID, participant, new TeamObjectives(10, 0, 1, 2, 0), null, null)));
        given(matchQueryPort.findTimelineEvents(List.of(MATCH_ID)))
                .willReturn(List.of(new Event(MATCH_ID, 1, "ITEM_PURCHASED", 1055, null, 60000)));

        // when
        GameReadModel game = service.getGame(MATCH_ID);

        // then
        assertThat(game.gameInfoData()).isEqualTo(gameInfo);
        assertThat(game.participantData().get(0).itemSeq()).hasSize(1);
        assertThat(game.teamInfoData().blueTeam().championKills()).isEqualTo(10);
    }

    @DisplayName("참가자가 아직 없는 매치도 빈 팀 정보를 둔다 (lol-server 단건 조회와 같다)")
    @Test
    void getGame_noParticipants_returnsEmptyTeam() {
        // given
        given(matchQueryPort.findGameInfo(MATCH_ID)).willReturn(Optional.of(gameInfo(MATCH_ID, 1000L)));
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

    @DisplayName("전적 목록은 페이지 매치들의 참가자·이벤트를 한 번씩 읽어 매치별로 나눠 조립한다")
    @Test
    void getSummonerMatches_dbPage_loadsParticipantsOncePerPage() {
        // given
        givenDbPage(null, null, 0, List.of(gameInfo("KR_2", 2000L), gameInfo("KR_1", 1000L)), true);
        given(matchQueryPort.findParticipants(List.of("KR_2", "KR_1"))).willReturn(List.of(
                new ParticipantRecord("KR_1", participant(1), new TeamObjectives(1, 0, 0, 0, 0), null, null),
                new ParticipantRecord("KR_2", participant(1), new TeamObjectives(2, 0, 0, 0, 0), null, null)));
        given(matchQueryPort.findTimelineEvents(List.of("KR_2", "KR_1"))).willReturn(List.of(
                new Event("KR_1", 1, "ITEM_PURCHASED", 1055, null, 60000)));
        given(matchCacheReadPort.findRecentMatchIds(PUUID)).willReturn(List.of());

        // when
        SliceResult<GameReadModel> result = service.getSummonerMatches(PUUID, null, null, 0);

        // then
        assertThat(result.hasNext()).isTrue();
        assertThat(result.content()).extracting(game -> game.teamInfoData().blueTeam().championKills())
                .containsExactly(2, 1);
        assertThat(result.content().get(0).participantData().get(0).itemSeq()).isNull();
        assertThat(result.content().get(1).participantData().get(0).itemSeq()).hasSize(1);
    }

    @DisplayName("캐시에만 있는 최신 매치를 DB 첫 페이지 위에 얹고 DB 꼬리는 자르지 않는다")
    @Test
    void getSummonerMatches_cacheOnlyMatch_prependedWithoutTruncatingDbPage() {
        // given
        givenDbPage(null, null, 0, List.of(gameInfo("KR_2", 2000L), gameInfo("KR_1", 1000L)), true);
        given(matchCacheReadPort.findRecentMatchIds(PUUID)).willReturn(List.of("KR_4", "KR_3", "KR_2"));
        given(matchCacheReadPort.findGames(List.of("KR_4", "KR_3"))).willReturn(Map.of(
                "KR_3", cachedGame("KR_3", 3000L, "CLASSIC", 420, "15.1.1"),
                "KR_4", cachedGame("KR_4", 4000L, "CLASSIC", 420, "15.1.1")));

        // when
        SliceResult<GameReadModel> result = service.getSummonerMatches(PUUID, null, null, 0);

        // then
        assertThat(matchIds(result)).containsExactly("KR_4", "KR_3", "KR_2", "KR_1");
        assertThat(result.hasNext()).isTrue();
    }

    @DisplayName("두 번째 페이지부터는 캐시를 보지 않는다")
    @Test
    void getSummonerMatches_secondPage_returnsDbResultWithoutCache() {
        // given
        givenDbPage(null, null, 1, List.of(gameInfo("KR_1", 1000L)), false);

        // when
        SliceResult<GameReadModel> result = service.getSummonerMatches(PUUID, null, null, 1);

        // then
        assertThat(matchIds(result)).containsExactly("KR_1");
        verifyNoInteractions(matchCacheReadPort);
    }

    @DisplayName("DB 첫 페이지 맨 아래보다 오래된 캐시 매치·단건 캐시에 없는 후보는 얹지 않는다")
    @Test
    void getSummonerMatches_olderThanPageFloorOrMissing_excludedFromOverlay() {
        // given
        givenDbPage(null, null, 0, List.of(gameInfo("KR_5", 5000L), gameInfo("KR_3", 3000L)), true);
        given(matchCacheReadPort.findRecentMatchIds(PUUID)).willReturn(List.of("KR_9", "KR_6", "KR_2"));
        given(matchCacheReadPort.findGames(List.of("KR_9", "KR_6", "KR_2"))).willReturn(Map.of(
                "KR_6", cachedGame("KR_6", 6000L, "CLASSIC", 420, "15.1.1"),
                "KR_2", cachedGame("KR_2", 2000L, "CLASSIC", 420, "15.1.1")));

        // when
        SliceResult<GameReadModel> result = service.getSummonerMatches(PUUID, null, null, 0);

        // then
        assertThat(matchIds(result)).containsExactly("KR_6", "KR_5", "KR_3");
    }

    @DisplayName("오버레이에도 DB 와 같은 필터 — 게임 모드(CLASSIC·CHERRY), 큐, 시즌(게임 버전 첫 숫자)")
    @Test
    void getSummonerMatches_filters_appliedToOverlay() {
        // given
        givenDbPage(15, 420, 0, List.of(), false);
        given(matchCacheReadPort.findRecentMatchIds(PUUID))
                .willReturn(List.of("ARAM", "FLEX", "OLD", "NOVER", "OK", "ARENA"));
        given(matchCacheReadPort.findGames(any())).willReturn(Map.of(
                "ARAM", cachedGame("ARAM", 6000L, "ARAM", 420, "15.1.1"),
                "FLEX", cachedGame("FLEX", 5000L, "CLASSIC", 440, "15.1.1"),
                "OLD", cachedGame("OLD", 4000L, "CLASSIC", 420, "14.24.1"),
                "NOVER", cachedGame("NOVER", 3500L, "CLASSIC", 420, null),
                "OK", cachedGame("OK", 3000L, "classic", 420, "15.2.1"),
                "ARENA", cachedGame("ARENA", 2000L, "CHERRY", 420, "15.2.1")));

        // when
        SliceResult<GameReadModel> result = service.getSummonerMatches(PUUID, 15, 420, 0);

        // then — DB 첫 페이지가 비어 있어도 캐시 매치를 돌려준다
        assertThat(matchIds(result)).containsExactly("OK", "ARENA");
        assertThat(result.hasNext()).isFalse();
    }

    @DisplayName("매치 ID 는 포트 결과 그대로, 음수 page 는 INVALID_INPUT")
    @Test
    void getSummonerMatchIds_page_delegatesAndRejectsNegative() {
        // given
        SliceResult<String> ids = new SliceResult<>(List.of("KR_2", "KR_1"), true);
        given(matchQueryPort.findSummonerMatchIds(PUUID, 420, 0, 20)).willReturn(ids);

        // when & then
        assertThat(service.getSummonerMatchIds(PUUID, 420, 0)).isEqualTo(ids);
        assertThatThrownBy(() -> service.getSummonerMatchIds(PUUID, null, -1))
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT));
        assertThatThrownBy(() -> service.getSummonerMatches(PUUID, null, null, -1))
                .isInstanceOf(CoreException.class);
        verify(matchQueryPort, never()).findSummonerGameInfos(any(), any(), any(), anyInt(), anyInt());
    }

    private void givenDbPage(Integer season, Integer queueId, int page, List<GameInfo> gameInfos, boolean hasNext) {
        given(matchQueryPort.findSummonerGameInfos(PUUID, season, queueId, page, 20))
                .willReturn(new SliceResult<>(gameInfos, hasNext));
        List<String> matchIds = gameInfos.stream().map(GameInfo::matchId).toList();
        given(matchQueryPort.findParticipants(matchIds)).willReturn(List.of());
        given(matchQueryPort.findTimelineEvents(matchIds)).willReturn(List.of());
    }

    private static List<String> matchIds(SliceResult<GameReadModel> result) {
        return result.content().stream().map(game -> game.gameInfoData().matchId()).toList();
    }

    private static GameInfo gameInfo(String matchId, long gameEndTimestamp) {
        return new GameInfo("2", 1L, 1800L, gameEndTimestamp, "CLASSIC", 1L, "MATCHED_GAME", "15.1.1", 11, "KR", 420,
                null, matchId, "GOLD", "IV");
    }

    private static GameReadModel cachedGame(String matchId, long gameEndTimestamp, String gameMode, int queueId,
                                            String gameVersion) {
        GameInfo gameInfo = new GameInfo("2", 1L, 1800L, gameEndTimestamp, gameMode, 1L, "MATCHED_GAME", gameVersion,
                11, "KR", queueId, null, matchId, null, null);
        return new GameReadModel(gameInfo, List.of(), null);
    }

    private static ParticipantReadModel participant(int participantId) {
        return ParticipantReadModel.builder().participantId(participantId).teamId(100).build();
    }
}
