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
@Table(name = "champion_skill_stat")
public class ChampionSkillStatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("챔피언 스킬 통계 ID")
    @Column(name = "champion_skill_stat_id")
    private Long id;

    @Embedded
    private StatDimensionValue dimension;

    @Comment("스킬 순서 (CSV)")
    private String skillOrder;

    @Comment("스킬 우선순위 (예: Q>E>W)")
    private String skillPriority;

    @Comment("게임 수")
    private long games;

    @Comment("승리 수")
    private long wins;
}
