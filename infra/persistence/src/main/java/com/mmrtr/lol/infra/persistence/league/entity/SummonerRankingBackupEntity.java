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
@Table(name = "summoner_ranking_backup")
public class SummonerRankingBackupEntity {

    @Id
    @Comment("백업 ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Comment("소환사 고유 식별자")
    private String puuid;

    @Comment("큐 타입")
    private String queue;

    @Comment("서버 지역")
    private String region;

    @Comment("현재 순위")
    private int currentRank;
}
