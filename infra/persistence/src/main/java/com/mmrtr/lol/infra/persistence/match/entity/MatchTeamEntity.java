package com.mmrtr.lol.infra.persistence.match.entity;


import com.mmrtr.lol.infra.riot.dto.match.BanDto;
import com.mmrtr.lol.infra.riot.dto.match.ObjectivesDto;
import com.mmrtr.lol.infra.riot.dto.match.TeamDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.util.List;

@Entity
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "match_team",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "unique_index_match_id_and_team_id",
                        columnNames = {"match_id", "team_id"}
                )
        }
)
public class MatchTeamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("매치 팀 ID")
    private Long id;

    @Comment("매치 ID")
    @Column(name = "match_id")
    private String matchId;

    @Comment("팀 ID")
    @Column(name = "team_id")
    private	int teamId;

    @Comment("승리 여부")
    private	boolean win;

    // Team Objective fields
    @Comment("바론 선취 여부")
    private	boolean baronFirst;
    @Comment("바론 처치 수")
    private	int baronKills;

    @Comment("챔피언 선취 여부")
    private	boolean championFirst;
    @Comment("챔피언 처치 수")
    private	int championKills;

    @Comment("드래곤 선취 여부")
    private	boolean dragonFirst;
    @Comment("드래곤 처치 수")
    private	int dragonKills;

    @Comment("억제기 선파괴 여부")
    private	boolean inhibitorFirst;
    @Comment("억제기 파괴 수")
    private	int inhibitorKills;

    @Comment("전령 선취 여부")
    private	boolean riftHeraldFirst;
    @Comment("전령 처치 수")
    private	int riftHeraldKills;

    @Comment("포탑 선파괴 여부")
    private	boolean towerFirst;
    @Comment("포탑 파괴 수")
    private	int towerKills;

    // Team Ban fields
    @Comment("밴 챔피언 1 ID")
    private	int champion1Id;
    @Comment("밴 1 순서")
    private	int pick1Turn;

    @Comment("밴 챔피언 2 ID")
    private	int champion2Id;
    @Comment("밴 2 순서")
    private	int pick2Turn;

    @Comment("밴 챔피언 3 ID")
    private	int champion3Id;
    @Comment("밴 3 순서")
    private	int pick3Turn;

    @Comment("밴 챔피언 4 ID")
    private	int champion4Id;
    @Comment("밴 4 순서")
    private	int pick4Turn;

    @Comment("밴 챔피언 5 ID")
    private	int champion5Id;
    @Comment("밴 5 순서")
    private	int pick5Turn;

    public static MatchTeamEntity of(MatchEntity match, TeamDto teamDto) {

        ObjectivesDto objectives = teamDto.getObjectives();
        List<BanDto> bans = teamDto.getBans();

        MatchTeamEntityBuilder builder = MatchTeamEntity.builder()
                .matchId(match.getMatchId())
                .teamId(teamDto.getTeamId())
                .win(teamDto.isWin())
                // Team Objective fields
                .baronFirst(objectives.getBaron().isFirst())
                .baronKills(objectives.getBaron().getKills())
                .championFirst(objectives.getChampion().isFirst())
                .championKills(objectives.getChampion().getKills())
                .dragonFirst(objectives.getDragon().isFirst())
                .dragonKills(objectives.getDragon().getKills())
                .inhibitorFirst(objectives.getInhibitor().isFirst())
                .inhibitorKills(objectives.getInhibitor().getKills())
                .riftHeraldFirst(objectives.getRiftHerald().isFirst())
                .riftHeraldKills(objectives.getRiftHerald().getKills())
                .towerFirst(objectives.getTower().isFirst())
                .towerKills(objectives.getTower().getKills());

        if(!bans.isEmpty()) {
            builder
                    .champion1Id(bans.get(0).getChampionId())
                    .pick1Turn(bans.get(0).getPickTurn())
                    .champion2Id(bans.get(1).getChampionId())
                    .pick2Turn(bans.get(1).getPickTurn())
                    .champion3Id(bans.get(2).getChampionId())
                    .pick3Turn(bans.get(2).getPickTurn())
                    .champion4Id(bans.get(3).getChampionId())
                    .pick4Turn(bans.get(3).getPickTurn())
                    .champion5Id(bans.get(4).getChampionId())
                    .pick5Turn(bans.get(4).getPickTurn());
        }

        return builder.build();
    }
}
