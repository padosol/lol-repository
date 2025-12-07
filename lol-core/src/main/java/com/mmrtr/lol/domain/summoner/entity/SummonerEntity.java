package com.mmrtr.lol.domain.summoner.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import com.mmrtr.lol.riot.dto.account.AccountDto;
import com.mmrtr.lol.riot.dto.summoner.SummonerDTO;
import com.mmrtr.lol.riot.type.Platform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummonerEntity {

    @Id
    private String puuid;
    private int profileIconId;
    private long revisionDate;
    private long summonerLevel;
    private String gameName;
    private String tagLine;
    private String region;
    private String searchName;
    private LocalDateTime revisionClickDate;

    public SummonerEntity(AccountDto accountDto, SummonerDTO summonerDTO, Platform platform) {
        this.puuid = summonerDTO.getPuuid();
        this.profileIconId = summonerDTO.getProfileIconId();
        this.revisionDate = summonerDTO.getRevisionDate();
        this.summonerLevel = summonerDTO.getSummonerLevel();
        this.gameName = accountDto.getGameName();
        this.tagLine = accountDto.getTagLine();
        this.region = platform.getRegion();
        this.searchName = (accountDto.getGameName().replace(" ", "") + "#" + accountDto.getTagLine()).toLowerCase();
        this.revisionClickDate = LocalDateTime.now();
    }

    public void initRevisionDate() {
        LocalDateTime revisionDateTime = LocalDateTime.of(2000, 1, 1, 1, 1, 1);
        this.revisionDate = revisionDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        this.revisionClickDate = revisionDateTime;
    }

}