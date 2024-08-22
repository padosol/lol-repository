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

    // update 가 true 이면 최근 갱신 되었다는 의미임.
    private boolean update;

    private boolean accountUpdate;
    private boolean summonerUpdate;
    private boolean leagueUpdate;
    private boolean matchUpdate;

    public SummonerRenewalSession() {};
    public SummonerRenewalSession(String puuid) {
        this.puuid = puuid;
        this.update = false;
        this.summonerUpdate = false;
        this.leagueUpdate = false;
        this.matchUpdate = false;
    }

    public void allUpdate(){
        this.accountUpdate = true;
        this.summonerUpdate = true;
        this.leagueUpdate = true;
        this.matchUpdate = true;
    }

    public void summonerUpdate() {
        this.summonerUpdate = true;
        updateCheck();
    }

    public void accountUpdate() {
        this.accountUpdate = true;
        updateCheck();
    }

    public void leagueUpdate() {
        this.leagueUpdate = true;
        updateCheck();
    }

    public void matchUpdate() {
        this.matchUpdate = true;
        updateCheck();
    }

    private void updateCheck() {
        this.update = this.summonerUpdate && this.accountUpdate && this.leagueUpdate && this.matchUpdate;
    }

}
