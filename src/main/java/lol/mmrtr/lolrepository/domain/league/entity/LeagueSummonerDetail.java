package lol.mmrtr.lolrepository.domain.league.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.sql.Timestamp;

@Entity
@Builder
@NoArgsConstructor
@Table(name = "league_summoner_detail")
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LeagueSummonerDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long leagueSummonerId;

    private int leaguePoints;
    private String rank;
    private int wins;
    private int losses;
    private boolean veteran;
    private boolean inactive;
    private boolean freshBlood;
    private boolean hotStreak;

    @CreatedDate
    private Timestamp createAt;

}
