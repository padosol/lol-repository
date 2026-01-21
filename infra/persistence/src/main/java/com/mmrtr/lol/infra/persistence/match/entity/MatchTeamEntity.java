package com.mmrtr.lol.infra.persistence.match.entity;


import com.mmrtr.lol.infra.persistence.match.entity.value.team.TeamBanValue;
import com.mmrtr.lol.infra.persistence.match.entity.value.team.TeamObjectValue;
import com.mmrtr.lol.infra.riot.dto.match.BanDto;
import com.mmrtr.lol.infra.riot.dto.match.ObjectivesDto;
import com.mmrtr.lol.infra.riot.dto.match.TeamDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    private Long id;

    @Column(name = "match_id")
    private String matchId;
    @Column(name = "team_id")
    private	int teamId;

    private	boolean win;

    @Embedded
    private TeamObjectValue teamObject;

    @Embedded
    private TeamBanValue teamBan;

    public static MatchTeamEntity of(MatchEntity match, TeamDto teamDto) {

        ObjectivesDto objectives = teamDto.getObjectives();
        TeamObjectValue teamObjectValue = TeamObjectValue.builder()
                .baronKills(objectives.getBaron().getKills())
                .baronFirst(objectives.getBaron().isFirst())
                .championKills(objectives.getChampion().getKills())
                .championFirst(objectives.getChampion().isFirst())
                .dragonKills(objectives.getDragon().getKills())
                .dragonFirst(objectives.getDragon().isFirst())
                .inhibitorKills(objectives.getInhibitor().getKills())
                .inhibitorFirst(objectives.getInhibitor().isFirst())
                .riftHeraldKills(objectives.getRiftHerald().getKills())
                .riftHeraldFirst(objectives.getRiftHerald().isFirst())
                .build();

        List<BanDto> bans = teamDto.getBans();

        TeamBanValue.TeamBanValueBuilder builder = TeamBanValue.builder();

        if(!bans.isEmpty()) {
            TeamBanValue.builder()
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

        return MatchTeamEntity.builder()
                .matchId(match.getMatchId())
                .teamId(teamDto.getTeamId())
                .win(teamDto.isWin())
                .teamObject(teamObjectValue)
                .build();
    }
}
