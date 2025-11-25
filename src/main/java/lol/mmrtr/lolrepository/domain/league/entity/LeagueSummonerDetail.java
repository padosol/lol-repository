package lol.mmrtr.lolrepository.domain.league.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Entity
@Builder
@NoArgsConstructor
@Table(name = "league_summoner_detail")
@AllArgsConstructor
public class LeagueSummonerDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long leagueSummonerId;

//    @ManyToOne
//    @JoinColumn(name = "league_summoner_id")
//    private LeagueSummoner leagueSummoner;

    private int leaguePoints;
    private String rank;
    private int wins;
    private int losses;
    private boolean veteran;
    private boolean inactive;
    private boolean freshBlood;
    private boolean hotStreak;

}
