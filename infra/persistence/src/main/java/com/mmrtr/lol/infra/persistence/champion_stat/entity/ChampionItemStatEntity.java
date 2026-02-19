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
@Table(name = "champion_item_stat")
public class ChampionItemStatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("챔피언 아이템 통계 ID")
    @Column(name = "champion_item_stat_id")
    private Long id;

    @Embedded
    private StatDimensionValue dimension;

    @Comment("빌드 타입 (STARTER, BOOTS, CORE)")
    private String buildType;

    @Comment("아이템 ID 목록 (정렬 CSV)")
    private String itemIds;

    @Comment("게임 수")
    private long games;

    @Comment("승리 수")
    private long wins;
}
