package com.mmrtr.lol.infra.persistence.champion_stat.entity;

import com.mmrtr.lol.infra.persistence.champion_stat.entity.value.StatDimensionValue;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "champion_rune_stat")
public class ChampionRuneStatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("챔피언 룬 통계 ID")
    @Column(name = "champion_rune_stat_id")
    private Long id;

    @Embedded
    private StatDimensionValue dimension;

    @Comment("주 룬 ID")
    private int primaryRuneId;

    @Comment("주 룬 세부 ID 목록 (CSV)")
    private String primaryRuneIds;

    @Comment("보조 룬 ID")
    private int secondaryRuneId;

    @Comment("보조 룬 세부 ID 목록 (CSV)")
    private String secondaryRuneIds;

    @Comment("공격 룬 스탯")
    private int statOffense;

    @Comment("유연 룬 스탯")
    private int statFlex;

    @Comment("방어 룬 스탯")
    private int statDefense;

    @Comment("게임 수")
    private long games;

    @Comment("승리 수")
    private long wins;
}
