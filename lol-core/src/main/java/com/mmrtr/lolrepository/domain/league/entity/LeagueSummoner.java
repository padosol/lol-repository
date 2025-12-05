package com.mmrtr.lolrepository.domain.league.entity;

import com.mmrtr.lolrepository.riot.type.Division;
import com.mmrtr.lolrepository.riot.type.Tier;
import jakarta.persistence.*;
import com.mmrtr.lolrepository.domain.league.entity.id.LeagueSummonerId;
import com.mmrtr.lolrepository.domain.summoner.entity.Summoner;
import com.mmrtr.lolrepository.riot.dto.league.LeagueEntryDTO;
import lombok.*;
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
public class LeagueSummoner {

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

    public LeagueSummoner(String puuid, String queue, String leagueId, int wins, int losses, String tier, String rank, int leaguePoints, boolean veteran, boolean inactive, boolean freshBlood, boolean hotStreak) {
        this.puuid = puuid;
        this.queue = queue;
        this.leagueId = leagueId;
        this.wins = wins;
        this.losses = losses;
        this.tier = tier;
        this.rank = rank;
        this.leaguePoints = leaguePoints;
        this.veteran = veteran;
        this.inactive = inactive;
        this.freshBlood = freshBlood;
        this.hotStreak = hotStreak;
        this.absolutePoints = calculatePoints();
    }

    public static LeagueSummoner of(String puuid, League league, LeagueEntryDTO leagueEntryDTO) {
        return new LeagueSummoner(
                puuid,
                league.getQueue(),
                league.getLeagueId(),
                leagueEntryDTO.getWins(),
                leagueEntryDTO.getLosses(),
                league.getTier(),
                leagueEntryDTO.getRank(),
                leagueEntryDTO.getLeaguePoints(),
                leagueEntryDTO.isVeteran(),
                leagueEntryDTO.isInactive(),
                leagueEntryDTO.isFreshBlood(),
                leagueEntryDTO.isHotStreak()
        );
    }

    public void changeLeague(League league, LeagueEntryDTO leagueEntryDTO) {
        this.queue = league.getQueue();
        this.leagueId = league.getLeagueId();
        this.wins = leagueEntryDTO.getWins();
        this.losses = leagueEntryDTO.getLosses();
        this.tier = leagueEntryDTO.getTier();
        this.rank = leagueEntryDTO.getRank();
        this.leaguePoints = leagueEntryDTO.getLeaguePoints();
        this.veteran = leagueEntryDTO.isVeteran();
        this.inactive = leagueEntryDTO.isInactive();
        this.freshBlood = leagueEntryDTO.isFreshBlood();
        this.hotStreak = leagueEntryDTO.isHotStreak();
        this.absolutePoints = calculatePoints();
    }

    private int calculatePoints() {
        int tierScore = Tier.valueOf(this.tier).getScore();
        int divisionScore = Division.valueOf(this.rank).getScore();

        return divisionScore + tierScore + this.leaguePoints;
    }

}
