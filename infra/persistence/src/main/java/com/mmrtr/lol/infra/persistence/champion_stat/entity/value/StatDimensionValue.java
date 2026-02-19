package com.mmrtr.lol.infra.persistence.champion_stat.entity.value;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import org.hibernate.annotations.Comment;

@Getter
@Setter
@Embeddable
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatDimensionValue {

    @Comment("챔피언 ID")
    @Column(name = "champion_id")
    private int championId;

    @Comment("팀 포지션")
    @Column(name = "team_position")
    private String teamPosition;

    @Comment("시즌")
    @Column(name = "season")
    private int season;

    @Comment("티어 그룹")
    @Column(name = "tier_group")
    private String tierGroup;

    @Comment("플랫폼 ID")
    @Column(name = "platform_id")
    private String platformId;

    @Comment("큐 ID")
    @Column(name = "queue_id")
    private int queueId;

    @Comment("게임 버전")
    @Column(name = "game_version")
    private String gameVersion;
}
