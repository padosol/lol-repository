package com.mmrtr.lol.infra.persistence.league.entity;

import com.mmrtr.lol.common.type.Division;
import com.mmrtr.lol.common.type.Tier;
import com.mmrtr.lol.domain.league.domain.LeagueSummoner;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
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
    @Comment("리그 소환사 ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "league_summoner_id")
    private Long id;

    @Comment("소환사 고유 식별자")
    private String puuid;
    @Comment("큐 타입")
    private String queue;

    @Comment("리그 ID")
    private String leagueId;

    @Comment("승리 수")
    private int wins;
    @Comment("패배 수")
    private int losses;
    @Comment("티어")
    private String tier;
    @Comment("랭크 (디비전)")
    private String rank;
    @Comment("리그 포인트 (LP)")
    private int leaguePoints;

    @Comment("절대 포인트 (티어+디비전+LP 환산)")
    private int absolutePoints;

    @Comment("베테랑 여부")
    private boolean veteran;
    @Comment("비활성 여부")
    private boolean inactive;
    @Comment("신규 진입 여부")
    private boolean freshBlood;
    @Comment("연승 여부")
    private boolean hotStreak;

    @CreatedDate
    @Comment("생성 일시")
    private LocalDateTime createAt;

    @LastModifiedBy
    @Comment("수정 일시")
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
