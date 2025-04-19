package lol.mmrtr.lolrepository.domain.league_summoner.entity;

import lol.mmrtr.lolrepository.message.LeagueSummonerMessage;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeagueSummoner {

    private String puuid;
    private String leagueId;
    private LocalDateTime createAt;
    private int leaguePoints;
    private String rank;
    private int wins;
    private int losses;
    private boolean veteran;
    private boolean inactive;
    private boolean freshBlood;
    private boolean hotStreak;


    public LeagueSummoner(LeagueSummonerMessage leagueSummonerMessage) {

        this.puuid = leagueSummonerMessage.getPuuid();
        this.leagueId = leagueSummonerMessage.getLeagueId();
        this.createAt = leagueSummonerMessage.getCreateAt();
        this.leaguePoints = leagueSummonerMessage.getLeaguePoints();
        this.rank = leagueSummonerMessage.getRank();
        this.wins = leagueSummonerMessage.getWins();
        this.losses = leagueSummonerMessage.getLosses();
        this.veteran = leagueSummonerMessage.isVeteran();
        this.inactive = leagueSummonerMessage.isInactive();
        this.freshBlood = leagueSummonerMessage.isFreshBlood();
        this.hotStreak = leagueSummonerMessage.isHotStreak();

    }
}
