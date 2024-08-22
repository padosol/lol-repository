package lol.mmrtr.lolrepository.redis.model;

import lombok.Getter;

import java.util.List;

@Getter
public class MatchRenewalSession {

    private List<String> matchIds;
    private String puuid;

    public MatchRenewalSession(){};
    public MatchRenewalSession(String puuid, List<String> matchIds) {
        this.puuid = puuid;
        this.matchIds = matchIds;
    }
}
