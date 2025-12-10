package com.mmrtr.lol.domain.match.entity;

import jakarta.persistence.*;
import com.mmrtr.lol.domain.match.entity.id.MatchTeamId;
import com.mmrtr.lol.domain.match.entity.value.team.TeamBanValue;
import com.mmrtr.lol.domain.match.entity.value.team.TeamObjectValue;
import com.mmrtr.lol.riot.dto.match.BanDto;
import com.mmrtr.lol.riot.dto.match.ObjectivesDto;
import com.mmrtr.lol.riot.dto.match.TeamDto;
import lombok.*;

import java.util.List;

@Entity
@Getter
@Builder
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
public class MatchTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id")
    private String matchId;
    @Column(name = "team_id")
    private	int teamId;

    private	boolean win;

    private	boolean baronFirst;
    private	int baronKills;

    private	boolean championFirst;
    private	int championKills;

    private	boolean dragonFirst;
    private	int dragonKills;

    private	boolean inhibitorFirst;
    private	int inhibitorKills;

    private	boolean riftHeraldFirst;
    private	int riftHeraldKills;

    private	boolean towerFirst;
    private	int towerKills;

    private	int champion1Id;
    private	int pick1Turn;

    private	int champion2Id;
    private	int pick2Turn;

    private	int champion3Id;
    private	int pick3Turn;

    private	int champion4Id;
    private	int pick4Turn;

    private	int champion5Id;
    private	int pick5Turn;

    public static MatchTeam of(Match match, TeamDto teamDto) {
        MatchTeamBuilder builder = MatchTeam.builder();

        ObjectivesDto objectives = teamDto.getObjectives();
            builder
                .matchId(match.getMatchId())
                .teamId(teamDto.getTeamId())
                .win(teamDto.isWin())
                .baronKills(objectives.getBaron().getKills())
                .baronFirst(objectives.getBaron().isFirst())
                .championKills(objectives.getChampion().getKills())
                .championFirst(objectives.getChampion().isFirst())
                .dragonKills(objectives.getDragon().getKills())
                .dragonFirst(objectives.getDragon().isFirst())
                .inhibitorKills(objectives.getInhibitor().getKills())
                .inhibitorFirst(objectives.getInhibitor().isFirst())
                .riftHeraldKills(objectives.getRiftHerald().getKills())
                .riftHeraldFirst(objectives.getRiftHerald().isFirst());


        List<BanDto> bans = teamDto.getBans();
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
