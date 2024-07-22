package lol.mmrtr.lolrepository.message;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeagueMessage {
    private String leagueId;
    private String tier;
    private String name;
    private String queue;
}
