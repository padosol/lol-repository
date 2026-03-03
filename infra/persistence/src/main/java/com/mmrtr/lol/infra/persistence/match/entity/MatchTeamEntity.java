package com.mmrtr.lol.infra.persistence.match.entity;

import com.mmrtr.lol.infra.riot.dto.match.FeatsDto;
import com.mmrtr.lol.infra.riot.dto.match.ObjectivesDto;
import com.mmrtr.lol.infra.riot.dto.match.TeamDto;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

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
    private int teamId;

    @Comment("승리 여부")
    private boolean win;

    // Objectives - Atakhan
    @Comment("아타칸 선취 여부")
    private boolean atakhanFirst;
    @Comment("아타칸 처치 수")
    private int atakhanKills;

    // Objectives - Baron
    @Comment("바론 선취 여부")
    private boolean baronFirst;
    @Comment("바론 처치 수")
    private int baronKills;

    // Objectives - Champion
    @Comment("챔피언 선취 여부")
    private boolean championFirst;
    @Comment("챔피언 처치 수")
    private int championKills;

    // Objectives - Dragon
    @Comment("드래곤 선취 여부")
    private boolean dragonFirst;
    @Comment("드래곤 처치 수")
    private int dragonKills;

    // Objectives - Horde
    @Comment("무리 선취 여부")
    private boolean hordeFirst;
    @Comment("무리 처치 수")
    private int hordeKills;

    // Objectives - Inhibitor
    @Comment("억제기 선파괴 여부")
    private boolean inhibitorFirst;
    @Comment("억제기 파괴 수")
    private int inhibitorKills;

    // Objectives - Rift Herald
    @Comment("전령 선취 여부")
    private boolean riftHeraldFirst;
    @Comment("전령 처치 수")
    private int riftHeraldKills;

    // Objectives - Tower
    @Comment("포탑 선파괴 여부")
    private boolean towerFirst;
    @Comment("포탑 파괴 수")
    private int towerKills;

    // Feats
    @Comment("에픽 몬스터 킬 업적 상태")
    private int featEpicMonsterKill;
    @Comment("퍼스트 블러드 업적 상태")
    private int featFirstBlood;
    @Comment("퍼스트 타워 업적 상태")
    private int featFirstTurret;

    public static MatchTeamEntity of(MatchEntity match, TeamDto teamDto) {
        ObjectivesDto objectives = teamDto.getObjectives();

        MatchTeamEntityBuilder builder = MatchTeamEntity.builder()
                .matchId(match.getMatchId())
                .teamId(teamDto.getTeamId())
                .win(teamDto.isWin())
                // Objectives
                .baronFirst(objectives.getBaron() != null && objectives.getBaron().isFirst())
                .baronKills(objectives.getBaron() != null ? objectives.getBaron().getKills() : 0)
                .championFirst(objectives.getChampion() != null && objectives.getChampion().isFirst())
                .championKills(objectives.getChampion() != null ? objectives.getChampion().getKills() : 0)
                .dragonFirst(objectives.getDragon() != null && objectives.getDragon().isFirst())
                .dragonKills(objectives.getDragon() != null ? objectives.getDragon().getKills() : 0)
                .inhibitorFirst(objectives.getInhibitor() != null && objectives.getInhibitor().isFirst())
                .inhibitorKills(objectives.getInhibitor() != null ? objectives.getInhibitor().getKills() : 0)
                .riftHeraldFirst(objectives.getRiftHerald() != null && objectives.getRiftHerald().isFirst())
                .riftHeraldKills(objectives.getRiftHerald() != null ? objectives.getRiftHerald().getKills() : 0)
                .towerFirst(objectives.getTower() != null && objectives.getTower().isFirst())
                .towerKills(objectives.getTower() != null ? objectives.getTower().getKills() : 0)
                // New objectives
                .atakhanFirst(objectives.getAtakhan() != null && objectives.getAtakhan().isFirst())
                .atakhanKills(objectives.getAtakhan() != null ? objectives.getAtakhan().getKills() : 0)
                .hordeFirst(objectives.getHorde() != null && objectives.getHorde().isFirst())
                .hordeKills(objectives.getHorde() != null ? objectives.getHorde().getKills() : 0);

        // Feats
        FeatsDto feats = teamDto.getFeats();
        if (feats != null) {
            if (feats.getEpicMonsterKill() != null) {
                builder.featEpicMonsterKill(feats.getEpicMonsterKill().getFeatState());
            }
            if (feats.getFirstBlood() != null) {
                builder.featFirstBlood(feats.getFirstBlood().getFeatState());
            }
            if (feats.getFirstTurret() != null) {
                builder.featFirstTurret(feats.getFirstTurret().getFeatState());
            }
        }

        return builder.build();
    }
}
