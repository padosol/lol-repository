package lol.mmrtr.lolrepository.entity;

import lol.mmrtr.lolrepository.message.LeagueSummonerMessage;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class LeagueSummoner {

    private String summonerId;
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

    public LeagueSummoner(){};

    public LeagueSummoner(LeagueSummonerMessage leagueSummonerMessage) {

        this.summonerId = leagueSummonerMessage.getSummonerId();
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
