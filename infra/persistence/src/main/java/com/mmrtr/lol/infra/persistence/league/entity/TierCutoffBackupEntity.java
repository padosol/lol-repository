package com.mmrtr.lol.infra.persistence.league.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tier_cutoff_backup")
public class TierCutoffBackupEntity {

    @Id
    @Comment("백업 ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Comment("큐 타입")
    private String queue;

    @Comment("티어")
    private String tier;

    @Comment("서버 지역")
    private String region;

    @Comment("최소 리그 포인트")
    private int minLeaguePoints;

    @Comment("유저 수")
    private int userCount;
}
