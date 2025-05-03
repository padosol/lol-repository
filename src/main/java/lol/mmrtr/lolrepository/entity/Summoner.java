package lol.mmrtr.lolrepository.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lol.mmrtr.lolrepository.message.SummonerMessage;
import lol.mmrtr.lolrepository.riot.dto.account.AccountDto;
import lol.mmrtr.lolrepository.riot.dto.summoner.SummonerDTO;
import lol.mmrtr.lolrepository.riot.type.Platform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Summoner{

    @Id
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

    public Summoner(AccountDto accountDto, SummonerDTO summonerDTO, Platform platform) {
        this.summonerId = summonerDTO.getId();
        this.accountId = summonerDTO.getAccountId();
        this.puuid = summonerDTO.getPuuid();
        this.profileIconId = summonerDTO.getProfileIconId();
        this.revisionDate = summonerDTO.getRevisionDate();
        this.summonerLevel = summonerDTO.getSummonerLevel();
        this.gameName = accountDto.getGameName();
        this.tagLine = accountDto.getTagLine();
        this.region = platform.getRegion();
        this.revisionClickDate = LocalDateTime.now();
    }
}