package com.mmrtr.lolrepository.domain.match.entity;

import jakarta.persistence.*;
import com.mmrtr.lolrepository.domain.match.entity.id.MatchTeamId;
import com.mmrtr.lolrepository.domain.match.entity.value.team.TeamBanValue;
import com.mmrtr.lolrepository.domain.match.entity.value.team.TeamObjectValue;
import com.mmrtr.lolrepository.riot.dto.match.BanDto;
import com.mmrtr.lolrepository.riot.dto.match.MatchDto;
import com.mmrtr.lolrepository.riot.dto.match.ObjectivesDto;
import com.mmrtr.lolrepository.riot.dto.match.TeamDto;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "match_team")
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MatchTeam {

    @EmbeddedId
    private MatchTeamId matchTeamId;

    @MapsId("matchId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;


    private	boolean win;

    @Embedded
    private TeamObjectValue teamObject;

    @Embedded
    private TeamBanValue teamBan;

    public static MatchTeam of(Match match, TeamDto teamDto) {

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

        return new MatchTeam(
                new MatchTeamId(
                        match.getMatchId(),
                        teamDto.getTeamId()
                ),
                match,
                teamDto.isWin(),
                teamObjectValue,
                builder.build()
        );
    }

}
