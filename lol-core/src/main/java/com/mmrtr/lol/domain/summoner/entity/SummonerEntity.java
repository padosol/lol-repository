package com.mmrtr.lol.domain.summoner.entity;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import com.mmrtr.lol.riot.dto.account.AccountDto;
import com.mmrtr.lol.riot.dto.summoner.SummonerDto;
import com.mmrtr.lol.riot.type.Platform;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Getter
@Entity
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "summoner")
public class SummonerEntity {

    @Id
    private String puuid;
    private int profileIconId;
    private long summonerLevel;
    private String gameName;
    private String tagLine;
    private String region;
    private String searchName;
    private LocalDateTime revisionDate;
    private LocalDateTime revisionClickDate;

    public SummonerEntity(AccountDto accountDto, SummonerDto summonerDTO, Platform platform) {
        this.puuid = summonerDTO.getPuuid();
        this.profileIconId = summonerDTO.getProfileIconId();

        Instant instant = Instant.ofEpochMilli(summonerDTO.getRevisionDate());
        ZoneId zoneId = ZoneId.systemDefault();
        this.revisionDate = LocalDateTime.ofInstant(instant, zoneId);
        this.summonerLevel = summonerDTO.getSummonerLevel();
        this.gameName = accountDto.getGameName();
        this.tagLine = accountDto.getTagLine();
        this.region = platform.getPlatformId();
        this.searchName = (accountDto.getGameName().replace(" ", "") + "#" + accountDto.getTagLine()).toLowerCase();
        this.revisionClickDate = LocalDateTime.now();
    }

    public static SummonerEntity of(Summoner summoner) {
        return SummonerEntity.builder()
                .puuid(summoner.getPuuid())
                .profileIconId(summoner.getStatusInfo().profileIconId())
                .revisionDate(summoner.getRevisionInfo().revisionDate())
                .summonerLevel(summoner.getStatusInfo().summonerLevel())
                .region(summoner.getPlatformId())
                .gameName(summoner.getGameIdentity().gameName())
                .tagLine(summoner.getGameIdentity().tagLine())
                .searchName((summoner.getGameIdentity().gameName().replace(" ", "")).toLowerCase())
                .revisionClickDate(LocalDateTime.now())
                .build();
    }

    public void initRevisionDate() {
        LocalDateTime revisionDateTime = LocalDateTime.of(2000, 1, 1, 1, 1, 1);
        this.revisionDate = revisionDateTime;
        this.revisionClickDate = revisionDateTime;
    }

}