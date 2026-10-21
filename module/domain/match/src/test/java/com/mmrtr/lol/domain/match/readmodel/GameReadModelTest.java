package com.mmrtr.lol.domain.match.readmodel;

import com.mmrtr.lol.domain.match.readmodel.GameReadModel.GameInfo;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.ParticipantRecord;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.TeamInfo;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel.TeamObjectives;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.Event;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.ItemSeq;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.SkillSeq;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameReadModelTest {

    private static final String MATCH_ID = "KR_1";

    @DisplayName("참가자에 자기 번호의 아이템·스킬 순서를 붙이고, 이벤트가 없는 참가자는 null 로 둔다")
    @Test
    void assemble_events_attachSequencesByParticipantId() {
        // given
        List<ParticipantRecord> participants = List.of(
                participantRecord(participant(1, 100, true), objectives(10), List.of(500), List.of(60000)),
                participantRecord(participant(2, 200, false), objectives(5), List.of(400), List.of(60000)));
        List<Event> events = List.of(
                new Event(MATCH_ID, 1, "ITEM_PURCHASED", 1055, null, 60000),
                new Event(MATCH_ID, 1, "SKILL_LEVEL_UP", null, 1, 120000));

        // when
        GameReadModel game = GameReadModel.assemble(gameInfo(420), participants, events);

        // then
        ParticipantReadModel first = game.participantData().get(0);
        assertThat(first.itemSeq()).containsExactly(new ItemSeq(1055, 1, "ITEM_PURCHASED"));
        assertThat(first.skillSeq()).containsExactly(new SkillSeq(1, 2, "SKILL_LEVEL_UP"));
        assertThat(game.participantData().get(1).itemSeq()).isNull();
        assertThat(game.participantData().get(1).skillSeq()).isNull();
    }

    @DisplayName("팀 정보는 첫 팀원의 승패·오브젝트와, 첫 기록의 프레임 수만큼 더한 팀원 골드다")
    @Test
    void assemble_teamMembers_buildsTeamFromFirstMemberAndSumsGold() {
        // given — 2번은 프레임 기록이 짧고 3번은 기록이 없다
        List<ParticipantRecord> participants = List.of(
                participantRecord(participant(1, 100, true), objectives(25), List.of(500, 3000, 8000), List.of(0, 60000, 120000)),
                participantRecord(participant(2, 100, true), objectives(25), List.of(450, 2800), List.of(0, 60000)),
                participantRecord(participant(3, 100, true), objectives(25), null, null),
                participantRecord(participant(4, 200, false), objectives(9), null, null));

        // when
        GameReadModel game = GameReadModel.assemble(gameInfo(420), participants, List.of());

        // then
        assertThat(game.teamInfoData().blueTeam()).isEqualTo(new TeamInfo(100, true, 25, 1, 2, 3, 4,
                List.of(950, 5800, 8000), List.of(0, 60000, 120000)));
        assertThat(game.teamInfoData().redTeam()).isEqualTo(new TeamInfo(200, false, 9, 1, 2, 3, 4, null, null));
    }

    @DisplayName("한 팀만 있으면 다른 팀은 null, 참가자가 없으면 팀 정보가 null")
    @Test
    void assemble_missingTeams_leavesNull() {
        // when
        GameReadModel oneTeam = GameReadModel.assemble(gameInfo(420),
                List.of(participantRecord(participant(1, 100, true), objectives(1), null, null)), List.of());
        GameReadModel empty = GameReadModel.assemble(gameInfo(420), List.of(), List.of());

        // then
        assertThat(oneTeam.teamInfoData().redTeam()).isNull();
        assertThat(empty.teamInfoData()).isNull();
        assertThat(empty.participantData()).isEmpty();
    }

    @DisplayName("아레나(1700·1710)는 참가자를 순위순으로, 그 밖의 큐는 참가자 번호순 그대로")
    @Test
    void assemble_arenaQueue_sortsByPlacement() {
        // given
        List<ParticipantRecord> participants = List.of(
                participantRecord(participant(1, 100, false).toBuilder().placement(3).build(), objectives(0), null, null),
                participantRecord(participant(2, 100, true).toBuilder().placement(1).build(), objectives(0), null, null),
                participantRecord(participant(3, 200, false).toBuilder().placement(2).build(), objectives(0), null, null));

        // when
        GameReadModel arena = GameReadModel.assemble(gameInfo(1700), participants, List.of());
        GameReadModel ranked = GameReadModel.assemble(gameInfo(420), participants, List.of());

        // then
        assertThat(arena.participantData()).extracting(ParticipantReadModel::participantId).containsExactly(2, 3, 1);
        assertThat(ranked.participantData()).extracting(ParticipantReadModel::participantId).containsExactly(1, 2, 3);
    }

    @DisplayName("평균 티어 점수(티어 + 등급 I=100…IV=400 + LP)를 티어·등급으로, 마스터 이상은 등급 없음")
    @Test
    void averageTierOf_points_mapsLikeLolServer() {
        // when & then
        assertThat(GameInfo.averageTierOf(4450)).isEqualTo("GOLD");
        assertThat(GameInfo.averageRankOf(4450)).isEqualTo("IV");
        assertThat(GameInfo.averageRankOf(4120)).isEqualTo("I");
        assertThat(GameInfo.averageRankOf(4050)).isEqualTo("I");
        assertThat(GameInfo.averageTierOf(8300)).isEqualTo("MASTER");
        assertThat(GameInfo.averageRankOf(8300)).isNull();
        assertThat(GameInfo.averageTierOf(500)).isEqualTo("IRON");
        assertThat(GameInfo.averageTierOf(null)).isNull();
        assertThat(GameInfo.averageRankOf(null)).isNull();
    }

    private static GameInfo gameInfo(int queueId) {
        return new GameInfo("2", 1L, 1800L, 2L, "CLASSIC", 1L, "MATCHED_GAME", "15.1.1", 11, "KR", queueId, null,
                MATCH_ID, null, null);
    }

    private static ParticipantReadModel participant(int participantId, int teamId, boolean win) {
        return ParticipantReadModel.builder().participantId(participantId).teamId(teamId).win(win).build();
    }

    private static TeamObjectives objectives(int championKills) {
        return new TeamObjectives(championKills, 1, 2, 3, 4);
    }

    private static ParticipantRecord participantRecord(ParticipantReadModel participant, TeamObjectives team,
                                                       List<Integer> goldTimeline, List<Integer> timestamps) {
        return new ParticipantRecord(MATCH_ID, participant, team, goldTimeline, timestamps);
    }
}
