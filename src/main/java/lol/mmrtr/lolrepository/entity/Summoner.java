package lol.mmrtr.lolrepository.entity;

import lol.mmrtr.lolrepository.message.SummonerMessage;
import lol.mmrtr.lolrepository.riot.dto.account.AccountDto;
import lol.mmrtr.lolrepository.riot.dto.summoner.SummonerDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Summoner{

    private String summonerId;
    private String accountId;
    private String puuid;
    private int profileIconId;
    private long revisionDate;
    private long summonerLevel;
    private String gameName;
    private String tagLine;
    private String region;
    private LocalDateTime revisionClickDate;

    public Summoner(SummonerMessage summonerMessage) {
        this.summonerId = summonerMessage.getId();
        this.accountId = summonerMessage.getAccountId();
        this.puuid = summonerMessage.getPuuid();
        this.profileIconId = summonerMessage.getProfileIconId();
        this.revisionDate = summonerMessage.getRevisionDate();
        this.summonerLevel = summonerMessage.getSummonerLevel();
        this.gameName = summonerMessage.getGameName();
        this.tagLine = summonerMessage.getTagLine();
        this.region = summonerMessage.getRegion();
        this.revisionClickDate = summonerMessage.getRevisionClickDate();
    }
}