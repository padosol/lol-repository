package com.mmrtr.lol.domain.match.readmodel;

import com.mmrtr.lol.common.type.Division;
import com.mmrtr.lol.common.type.Tier;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.Event;
import com.mmrtr.lol.domain.match.readmodel.MatchTimelineReadModel.ParticipantTimeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 매치 한 판 — 공개 매치 API 의 응답. 필드·이름·순서가 lol-server {@code GameResponse} 트리와 같다
 * ({@code MatchControllerTest} 가 검증). 저장된 매치는 {@link #assemble} 로 조립한다.
 *
 * @param teamInfoData 참가자가 없으면 null
 */
public record GameReadModel(
        GameInfo gameInfoData,
        List<ParticipantReadModel> participantData,
        Team teamInfoData
) {

    private static final int BLUE_TEAM_ID = 100;
    private static final int RED_TEAM_ID = 200;

    /** 아레나는 참가자를 순위순으로 보여준다. */
    private static final Set<Integer> ARENA_QUEUE_IDS = Set.of(1700, 1710);

    /**
     * @param averageTier 참가자 평균 티어 ({@link #averageTierOf}), 없으면 null
     * @param averageRank 평균 티어의 등급, 등급이 없는 티어(마스터 이상)거나 평균이 없으면 null
     */
    public record GameInfo(
            String dataVersion,
            long gameCreation,
            long gameDuration,
            long gameEndTimestamp,
            String gameMode,
            long gameStartTimestamp,
            String gameType,
            String gameVersion,
            int mapId,
            String platformId,
            int queueId,
            String tournamentCode,
            String matchId,
            String averageTier,
            String averageRank
    ) {

        /** @param points 저장된 평균 티어 점수 ({@code match.average_tier} — 티어 점수 + 등급 점수 + LP) */
        public static String averageTierOf(Integer points) {
            return points == null ? null : tierOf(points).name();
        }

        public static String averageRankOf(Integer points) {
            if (points == null) {
                return null;
            }
            Tier tier = tierOf(points);
            if (tier == Tier.MASTER || tier == Tier.GRANDMASTER || tier == Tier.CHALLENGER) {
                return null;
            }
            return divisionOf(points - tier.getScore()).name();
        }

        /** 점수 이하인 가장 높은 티어. lol-server {@code Tier.fromAbsolutePoints} 와 같다. */
        private static Tier tierOf(int points) {
            for (Tier tier : Tier.values()) {
                if (tier.getScore() <= points) {
                    return tier;
                }
            }
            return Tier.IRON;
        }

        /** 남은 점수 이하인 가장 큰 등급 점수. lol-server {@code Division.fromRemainingPoints} 와 같다. */
        private static Division divisionOf(int remainingPoints) {
            Division[] divisions = Division.values();
            for (int i = divisions.length - 1; i >= 0; i--) {
                if (divisions[i].getScore() <= remainingPoints) {
                    return divisions[i];
                }
            }
            return Division.I;
        }
    }

    /** @param blueTeam 블루팀 참가자가 없으면 null */
    public record Team(TeamInfo blueTeam, TeamInfo redTeam) {
    }

    /**
     * @param goldTimeline 팀원 골드를 프레임별로 더한 값. 프레임 기록이 없으면 null
     * @param timestamps   골드 타임라인의 프레임 시각(ms). 프레임 기록이 없으면 null
     */
    public record TeamInfo(int teamId, boolean win, int championKills, int baronKills, int dragonKills,
                           int towerKills, int inhibitorKills, List<Integer> goldTimeline,
                           List<Integer> timestamps) {
    }

    /**
     * 저장된 참가자 한 명 ({@code match_participant}) — 그 팀의 오브젝트 집계({@code match_team})와
     * 참가자의 프레임별 골드({@code participant_frame})를 함께 읽는다.
     *
     * @param goldTimeline 프레임 시각순 누적 골드, 프레임 기록이 없으면 null
     * @param timestamps   프레임 시각(ms), 프레임 기록이 없으면 null
     */
    public record ParticipantRecord(String matchId, ParticipantReadModel participant, TeamObjectives team,
                                    List<Integer> goldTimeline, List<Integer> timestamps) {
    }

    /** 팀 오브젝트 처치 수. 팀 기록이 없으면 모두 0. */
    public record TeamObjectives(int championKills, int baronKills, int dragonKills, int towerKills,
                                 int inhibitorKills) {
    }

    /**
     * 참가자에 아이템·스킬 순서를 붙이고 팀 정보를 만든다. lol-server {@code MatchPersistenceAdapter} 의 조립과 같다:
     * 팀 정보는 그 팀 첫 참가자의 승패·오브젝트 집계와 팀원 골드의 합이다.
     *
     * @param participants 한 매치의 참가자 (참가자 번호순)
     * @param events       한 매치의 타임라인 이벤트 (프레임 시각·이벤트 순)
     */
    public static GameReadModel assemble(GameInfo gameInfo, List<ParticipantRecord> participants, List<Event> events) {
        Map<Integer, ParticipantTimeline> timelines = MatchTimelineReadModel.from(events).participants();
        List<ParticipantReadModel> participantData = new ArrayList<>();
        for (ParticipantRecord participantRecord : participants) {
            ParticipantReadModel participant = participantRecord.participant();
            participantData.add(participant.withTimeline(timelines.get(participant.participantId())));
        }
        if (ARENA_QUEUE_IDS.contains(gameInfo.queueId())) {
            participantData.sort(Comparator.comparingInt(ParticipantReadModel::placement));
        }

        Team team = participants.isEmpty()
                ? null
                : new Team(teamInfo(participants, BLUE_TEAM_ID), teamInfo(participants, RED_TEAM_ID));
        return new GameReadModel(gameInfo, participantData, team);
    }

    private static TeamInfo teamInfo(List<ParticipantRecord> participants, int teamId) {
        List<ParticipantRecord> members = participants.stream()
                .filter(member -> member.participant().teamId() == teamId)
                .toList();
        if (members.isEmpty()) {
            return null;
        }
        ParticipantRecord first = members.get(0);
        TeamObjectives objectives = first.team();
        return new TeamInfo(teamId, first.participant().win(), objectives.championKills(), objectives.baronKills(),
                objectives.dragonKills(), objectives.towerKills(), objectives.inhibitorKills(),
                sumGoldTimelines(members), firstTimestamps(members));
    }

    /**
     * 첫 번째로 기록이 있는 팀원의 프레임 수만큼, 팀원 골드를 프레임별로 더한다.
     * 기록이 없으면 null — lol-server 응답과 같이 빈 배열이 아니다.
     */
    private static List<Integer> sumGoldTimelines(List<ParticipantRecord> members) {
        return members.stream()
                .map(ParticipantRecord::goldTimeline)
                .filter(gold -> gold != null && !gold.isEmpty())
                .findFirst()
                .map(first -> sumGold(members, first.size()))
                .orElse(null);
    }

    private static List<Integer> sumGold(List<ParticipantRecord> members, int frames) {
        int[] sum = new int[frames];
        for (ParticipantRecord member : members) {
            List<Integer> gold = member.goldTimeline();
            if (gold == null) {
                continue;
            }
            for (int i = 0; i < Math.min(sum.length, gold.size()); i++) {
                sum[i] += gold.get(i) == null ? 0 : gold.get(i);
            }
        }
        return Arrays.stream(sum).boxed().toList();
    }

    private static List<Integer> firstTimestamps(List<ParticipantRecord> members) {
        return members.stream()
                .map(ParticipantRecord::timestamps)
                .filter(timestamps -> timestamps != null && !timestamps.isEmpty())
                .findFirst()
                .orElse(null);
    }
}
