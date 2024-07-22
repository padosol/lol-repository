package lol.mmrtr.lolrepository.entity;

import lol.mmrtr.lolrepository.message.LeagueMessage;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class League {
    private String leagueId;
    private String tier;
    private String name;
    private String queue;

    public League(){};

    public League(LeagueMessage leagueMessage) {
        this.leagueId = leagueMessage.getLeagueId();
        this.tier = leagueMessage.getTier();
        this.name = leagueMessage.getName();
        this.queue = leagueMessage.getQueue();
    }
}
