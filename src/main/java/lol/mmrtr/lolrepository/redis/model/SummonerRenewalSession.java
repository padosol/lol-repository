package lol.mmrtr.lolrepository.redis.model;


import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serializable;

@Getter
@Setter
@RedisHash(value = "summonerRenewal", timeToLive = 120)
public class SummonerRenewalSession implements Serializable {

    @Id
    private String puuid;

    private boolean summonerUpdate;
    private boolean leagueUpdate;
    private boolean matchUpdate;

    public SummonerRenewalSession() {}
    public SummonerRenewalSession(String puuid) {
        this.puuid = puuid;
        this.summonerUpdate = false;
        this.leagueUpdate = false;
        this.matchUpdate = false;
    }

    public void allUpdate(){
        this.summonerUpdate = true;
        this.leagueUpdate = true;
        this.matchUpdate = true;
    }

    public void summonerUpdate() {
        this.summonerUpdate = true;
    }

    public void leagueUpdate() {
        this.leagueUpdate = true;
    }

    public void matchUpdate() {
        this.matchUpdate = true;
    }

}
