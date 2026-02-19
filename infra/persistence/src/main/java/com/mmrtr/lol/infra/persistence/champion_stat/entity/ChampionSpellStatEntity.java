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
@Table(name = "champion_spell_stat")
public class ChampionSpellStatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("챔피언 스펠 통계 ID")
    @Column(name = "champion_spell_stat_id")
    private Long id;

    @Embedded
    private StatDimensionValue dimension;

    @Comment("소환사 주문 1 ID (작은 값)")
    private int spell1Id;

    @Comment("소환사 주문 2 ID (큰 값)")
    private int spell2Id;

    @Comment("게임 수")
    private long games;

    @Comment("승리 수")
    private long wins;
}
