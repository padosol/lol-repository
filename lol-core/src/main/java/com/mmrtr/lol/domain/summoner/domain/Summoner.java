package com.mmrtr.lol.domain.summoner.domain;

import com.mmrtr.lol.domain.summoner.domain.vo.GameIdentity;
import com.mmrtr.lol.domain.summoner.domain.vo.Region;
import com.mmrtr.lol.domain.summoner.domain.vo.RevisionInfo;
import com.mmrtr.lol.domain.summoner.domain.vo.StatusInfo;
import com.mmrtr.lol.riot.dto.account.AccountDto;
import com.mmrtr.lol.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.riot.dto.summoner.SummonerDto;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
public class Summoner {
    private String puuid;
    private GameIdentity gameIdentity;
    private String platformId;
    private StatusInfo statusInfo;
    private RevisionInfo revisionInfo;
    private Set<LeagueEntryDto> leagueEntryDtos;

    public static Summoner of(AccountDto accountDto,
                              SummonerDto summonerDTO,
                              String platformId,
                              Set<LeagueEntryDto> leagueEntryDtos) {
        return Summoner.builder()
                .puuid(accountDto.getPuuid())
                .gameIdentity(new GameIdentity(accountDto.getGameName(), accountDto.getTagLine()))
                .platformId(platformId)
                .statusInfo(new StatusInfo(summonerDTO.getProfileIconId(), summonerDTO.getSummonerLevel()))
                .revisionInfo(new RevisionInfo(
                        LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(summonerDTO.getRevisionDate()), ZoneId.systemDefault()
                        ),
                        LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(summonerDTO.getRevisionDate()), ZoneId.systemDefault()
                        )
                ))
                .leagueEntryDtos(leagueEntryDtos)
                .build();
    }
}
