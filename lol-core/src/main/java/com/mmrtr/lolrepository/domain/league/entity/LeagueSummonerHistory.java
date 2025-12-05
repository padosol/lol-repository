package com.mmrtr.lolrepository.domain.league.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "league_summoner_history",
        indexes = {
                @Index(name = "idx_puuid_queue", columnList = "puuid, queue")
        }
)
public class LeagueSummonerHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long leagueSummonerId;

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
    private long absolutePoints;
    private boolean veteran;
    private boolean inactive;
    private boolean freshBlood;
    private boolean hotStreak;

    @CreatedDate
    private LocalDateTime createdAt;

    public static LeagueSummonerHistory create(LeagueSummoner leagueSummoner) {
        return LeagueSummonerHistory.builder()
                .leagueSummonerId(leagueSummoner.getId())
                .puuid(leagueSummoner.getPuuid())
                .queue(leagueSummoner.getQueue())
                .leagueId(leagueSummoner.getLeagueId())
                .wins(leagueSummoner.getWins())
                .losses(leagueSummoner.getLosses())
                .tier(leagueSummoner.getTier())
                .rank(leagueSummoner.getRank())
                .leaguePoints(leagueSummoner.getLeaguePoints())
                .absolutePoints(leagueSummoner.getAbsolutePoints())
                .veteran(leagueSummoner.isVeteran())
                .inactive(leagueSummoner.isInactive())
                .freshBlood(leagueSummoner.isFreshBlood())
                .hotStreak(leagueSummoner.isHotStreak())
                .build();
    }
}
