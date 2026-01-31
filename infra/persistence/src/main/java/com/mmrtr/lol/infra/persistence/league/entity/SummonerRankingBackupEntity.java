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
@Table(name = "summoner_ranking_backup")
public class SummonerRankingBackupEntity {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "puuid", nullable = false, length = 100)
    private String puuid;

    @Column(name = "queue", nullable = false, length = 50)
    private String queue;

    @Column(name = "current_rank", nullable = false)
    private int currentRank;
}
