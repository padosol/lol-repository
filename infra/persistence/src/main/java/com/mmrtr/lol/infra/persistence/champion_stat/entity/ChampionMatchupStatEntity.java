package com.mmrtr.lol.infra.persistence.champion_stat.entity;

import com.mmrtr.lol.infra.persistence.champion_stat.entity.value.StatDimensionValue;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.math.BigDecimal;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "champion_matchup_stat")
public class ChampionMatchupStatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("챔피언 매치업 통계 ID")
    @Column(name = "champion_matchup_stat_id")
    private Long id;

    @Embedded
    private StatDimensionValue dimension;

    @Comment("상대 챔피언 ID")
    private int opponentChampionId;

    @Comment("게임 수")
    private long games;

    @Comment("승리 수")
    private long wins;

    @Comment("평균 킬")
    private BigDecimal avgKills;

    @Comment("평균 데스")
    private BigDecimal avgDeaths;

    @Comment("평균 어시스트")
    private BigDecimal avgAssists;

    @Comment("평균 골드 차이")
    private BigDecimal avgGoldDiff;
}
