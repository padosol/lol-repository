package com.mmrtr.lol.infra.persistence.match.entity;

import com.mmrtr.lol.domain.match.readmodel.BanDto;

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
        name = "match_ban",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "unique_index_match_ban",
                        columnNames = {"match_id", "team_id", "pick_turn"}
                )
        }
)
public class MatchBanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("매치 밴 ID")
    private Long id;

    @Comment("매치 ID")
    @Column(name = "match_id")
    private String matchId;

    @Comment("팀 ID")
    @Column(name = "team_id")
    private int teamId;

    @Comment("밴 챔피언 ID")
    @Column(name = "champion_id")
    private int championId;

    @Comment("밴 순서")
    @Column(name = "pick_turn")
    private int pickTurn;

    public static MatchBanEntity of(String matchId, int teamId, BanDto banDto) {
        return MatchBanEntity.builder()
                .matchId(matchId)
                .teamId(teamId)
                .championId(banDto.getChampionId())
                .pickTurn(banDto.getPickTurn())
                .build();
    }
}
