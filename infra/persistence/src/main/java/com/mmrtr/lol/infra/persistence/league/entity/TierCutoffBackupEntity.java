package com.mmrtr.lol.infra.persistence.league.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tier_cutoff_backup")
public class TierCutoffBackupEntity {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "queue", nullable = false, length = 50)
    private String queue;

    @Column(name = "tier", nullable = false, length = 20)
    private String tier;

    @Column(name = "region", nullable = false, length = 10)
    private String region;

    @Column(name = "min_league_points", nullable = false)
    private int minLeaguePoints;

    @Column(name = "user_count")
    private int userCount;
}
