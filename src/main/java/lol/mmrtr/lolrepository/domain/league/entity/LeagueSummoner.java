package lol.mmrtr.lolrepository.domain.league.entity;

import jakarta.persistence.*;
import lol.mmrtr.lolrepository.domain.league.entity.id.LeagueSummonerId;
import lol.mmrtr.lolrepository.domain.summoner.entity.Summoner;
import lol.mmrtr.lolrepository.riot.dto.league.LeagueEntryDTO;
import lombok.*;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeagueSummoner {

    @Id
    @Column(name = "league_summoner_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

//    @EmbeddedId
//    private LeagueSummonerId leagueSummonerId;
    private String puuid;
    private String leagueId;

//    @MapsId("puuid")
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "puuid")
//    private Summoner summoner;
//
//    @MapsId("leagueId")
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "league_id")
//    private League league;

//    private int leaguePoints;
//    private String rank;
//    private int wins;
//    private int losses;
//    private boolean veteran;
//    private boolean inactive;
//    private boolean freshBlood;
//    private boolean hotStreak;
//
//
//    public LeagueSummoner(LeagueSummonerId leagueSummonerId, Summoner summoner, League league, LeagueEntryDTO leagueEntryDTO) {
//        this.leagueSummonerId = leagueSummonerId;
//        this.summoner = summoner;
//        this.league = league;
//        this.leaguePoints = leagueEntryDTO.getLeaguePoints();
//        this.rank = leagueEntryDTO.getRank();
//        this.wins = leagueEntryDTO.getWins();
//        this.losses = leagueEntryDTO.getLosses();
//        this.veteran = leagueEntryDTO.isVeteran();
//        this.inactive = leagueEntryDTO.isInactive();
//        this.freshBlood = leagueEntryDTO.isFreshBlood();
//        this.hotStreak = leagueEntryDTO.isHotStreak();
//    }
}
