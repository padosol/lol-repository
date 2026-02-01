package com.mmrtr.lol.infra.persistence.league.entity;

import com.mmrtr.lol.domain.league.domain.SummonerRanking;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "summoner_ranking",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "unique_puuid_queue",
                        columnNames = {"puuid", "queue"}
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
public class SummonerRankingEntity {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "puuid", nullable = false, length = 100)
    private String puuid;

    @Column(name = "queue", nullable = false, length = 50)
    private String queue;

    @Column(name = "region", nullable = false, length = 10)
    private String region;

    @Column(name = "current_rank", nullable = false)
    private int currentRank;

    @Column(name = "rank_change")
    private int rankChange;

    @Column(name = "game_name", length = 50)
    private String gameName;

    @Column(name = "tag_line", length = 10)
    private String tagLine;

    @Column(name = "most_champion_1", length = 50)
    private String mostChampion1;

    @Column(name = "most_champion_2", length = 50)
    private String mostChampion2;

    @Column(name = "most_champion_3", length = 50)
    private String mostChampion3;

    @Column(name = "wins", nullable = false)
    private int wins;

    @Column(name = "losses", nullable = false)
    private int losses;

    @Column(name = "win_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal winRate;

    @Column(name = "tier", nullable = false, length = 20)
    private String tier;

    @Column(name = "rank", length = 5)
    private String rank;

    @Column(name = "league_points", nullable = false)
    private int leaguePoints;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static SummonerRankingEntity fromDomain(SummonerRanking domain) {
        return SummonerRankingEntity.builder()
                .id(domain.getId())
                .puuid(domain.getPuuid())
                .queue(domain.getQueue())
                .region(domain.getRegion())
                .currentRank(domain.getCurrentRank())
                .rankChange(domain.getRankChange())
                .gameName(domain.getGameName())
                .tagLine(domain.getTagLine())
                .mostChampion1(domain.getMostChampion1())
                .mostChampion2(domain.getMostChampion2())
                .mostChampion3(domain.getMostChampion3())
                .wins(domain.getWins())
                .losses(domain.getLosses())
                .winRate(domain.getWinRate())
                .tier(domain.getTier())
                .rank(domain.getRank())
                .leaguePoints(domain.getLeaguePoints())
                .build();
    }

    public SummonerRanking toDomain() {
        return SummonerRanking.builder()
                .id(this.id)
                .puuid(this.puuid)
                .queue(this.queue)
                .region(this.region)
                .currentRank(this.currentRank)
                .rankChange(this.rankChange)
                .gameName(this.gameName)
                .tagLine(this.tagLine)
                .mostChampion1(this.mostChampion1)
                .mostChampion2(this.mostChampion2)
                .mostChampion3(this.mostChampion3)
                .wins(this.wins)
                .losses(this.losses)
                .winRate(this.winRate)
                .tier(this.tier)
                .rank(this.rank)
                .leaguePoints(this.leaguePoints)
                .build();
    }
}
