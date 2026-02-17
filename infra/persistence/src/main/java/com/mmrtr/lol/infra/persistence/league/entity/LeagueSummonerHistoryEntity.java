package com.mmrtr.lol.infra.persistence.league.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
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
public class LeagueSummonerHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("이력 ID")
    private Long id;

    @Comment("리그 소환사 ID")
    private Long leagueSummonerId;

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
    @Comment("절대 포인트")
    private long absolutePoints;
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
    private LocalDateTime createdAt;

    public static LeagueSummonerHistoryEntity fromLeagueSummonerEntity(LeagueSummonerEntity entity) {
        return LeagueSummonerHistoryEntity.builder()
                .leagueSummonerId(entity.getId())
                .puuid(entity.getPuuid())
                .queue(entity.getQueue())
                .leagueId(entity.getLeagueId())
                .wins(entity.getWins())
                .losses(entity.getLosses())
                .tier(entity.getTier())
                .rank(entity.getRank())
                .leaguePoints(entity.getLeaguePoints())
                .absolutePoints(entity.getAbsolutePoints())
                .veteran(entity.isVeteran())
                .inactive(entity.isInactive())
                .freshBlood(entity.isFreshBlood())
                .hotStreak(entity.isHotStreak())
                .build();
    }
}
