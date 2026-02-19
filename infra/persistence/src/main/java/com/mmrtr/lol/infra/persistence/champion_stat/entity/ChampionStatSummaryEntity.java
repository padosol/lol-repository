package com.mmrtr.lol.infra.persistence.champion_stat.entity;

import com.mmrtr.lol.infra.persistence.champion_stat.entity.value.StatDimensionValue;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.math.BigDecimal;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "champion_stat_summary")
public class ChampionStatSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("챔피언 통계 요약 ID")
    @Column(name = "champion_stat_summary_id")
    private Long id;

    @Embedded
    private StatDimensionValue dimension;

    @Comment("총 게임 수")
    private long totalGames;

    @Comment("승리 수")
    private long wins;

    @Comment("총 밴 수")
    private long totalBans;

    @Comment("해당 차원 총 매치 수")
    private long totalMatchesInDimension;

    @Comment("평균 킬")
    private BigDecimal avgKills;

    @Comment("평균 데스")
    private BigDecimal avgDeaths;

    @Comment("평균 어시스트")
    private BigDecimal avgAssists;

    @Comment("평균 CS")
    private BigDecimal avgCs;

    @Comment("평균 골드")
    private BigDecimal avgGold;
}
