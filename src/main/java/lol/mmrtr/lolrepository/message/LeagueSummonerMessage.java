package lol.mmrtr.lolrepository.message;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class LeagueSummonerMessage {

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

    public LeagueSummonerMessage(){};
}
