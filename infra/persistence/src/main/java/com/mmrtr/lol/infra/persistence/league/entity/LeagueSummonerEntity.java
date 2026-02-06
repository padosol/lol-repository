package com.mmrtr.lol.infra.persistence.league.entity;

import com.mmrtr.lol.common.type.Division;
import com.mmrtr.lol.common.type.Tier;
import com.mmrtr.lol.domain.league.domain.LeagueSummoner;
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
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "league_summoner",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "unique_index_puuid_and_queue",
                        columnNames = {"puuid", "queue"}
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
public class LeagueSummonerEntity {

    @Id
    @Column(name = "league_summoner_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String puuid;
    @Column(name = "queue")
    private String queue;

    @Column(name = "league_id")
    private String leagueId;

    private int wins;
    private int losses;
    private String tier;
    private String rank;
    private int leaguePoints;

    private int absolutePoints;

    private boolean veteran;
    private boolean inactive;
    private boolean freshBlood;
    private boolean hotStreak;

    @CreatedDate
    private LocalDateTime createAt;

    @LastModifiedBy
    private LocalDateTime updateAt;

    public static LeagueSummonerEntity fromDomain(LeagueSummoner domain) {
        LeagueSummonerEntity entity = LeagueSummonerEntity.builder()
                .puuid(domain.getPuuid())
                .queue(domain.getQueue())
                .leagueId(domain.getLeagueId())
                .wins(domain.getWins())
                .losses(domain.getLosses())
                .tier(domain.getTier())
                .rank(domain.getRank())
                .leaguePoints(domain.getLeaguePoints())
                .veteran(domain.isVeteran())
                .inactive(domain.isInactive())
                .freshBlood(domain.isFreshBlood())
                .hotStreak(domain.isHotStreak())
                .build();
        entity.absolutePoints = entity.calculatePoints();
        return entity;
    }

    public LeagueSummoner toDomain() {
        return LeagueSummoner.builder()
                .puuid(this.puuid)
                .leagueId(this.leagueId)
                .queue(this.queue)
                .tier(this.tier)
                .rank(this.rank)
                .leaguePoints(this.leaguePoints)
                .wins(this.wins)
                .losses(this.losses)
                .hotStreak(this.hotStreak)
                .veteran(this.veteran)
                .freshBlood(this.freshBlood)
                .inactive(this.inactive)
                .build();
    }

    private int calculatePoints() {
        int tierScore = Tier.valueOf(this.tier).getScore();
        int divisionScore = Division.valueOf(this.rank).getScore();

        return divisionScore + tierScore + this.leaguePoints;
    }

    public void update(LeagueSummoner domain) {
        this.leagueId = domain.getLeagueId();
        this.wins = domain.getWins();
        this.losses = domain.getLosses();
        this.tier = domain.getTier();
        this.rank = domain.getRank();
        this.leaguePoints = domain.getLeaguePoints();
        this.veteran = domain.isVeteran();
        this.inactive = domain.isInactive();
        this.freshBlood = domain.isFreshBlood();
        this.hotStreak = domain.isHotStreak();
        this.absolutePoints = calculatePoints();
    }
}
