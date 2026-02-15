package com.mmrtr.lol.infra.persistence.league.entity;

import com.mmrtr.lol.domain.league.domain.SummonerRanking;
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
import org.hibernate.annotations.Comment;
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
                        name = "unique_puuid_queue_region",
                        columnNames = {"puuid", "queue", "region"}
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
public class SummonerRankingEntity {

    @Id
    @Comment("랭킹 ID")
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

    @Comment("순위 변동")
    private int rankChange;

    @Comment("게임 닉네임")
    private String gameName;

    @Comment("태그라인")
    private String tagLine;

    @Comment("모스트 챔피언 1")
    private String mostChampion1;

    @Comment("모스트 챔피언 2")
    private String mostChampion2;

    @Comment("모스트 챔피언 3")
    private String mostChampion3;

    @Comment("승리 수")
    private int wins;

    @Comment("패배 수")
    private int losses;

    @Comment("승률")
    private BigDecimal winRate;

    @Comment("티어")
    private String tier;

    @Comment("랭크 (디비전)")
    private String rank;

    @Comment("리그 포인트 (LP)")
    private int leaguePoints;

    @CreatedDate
    @Comment("생성 일시")
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
