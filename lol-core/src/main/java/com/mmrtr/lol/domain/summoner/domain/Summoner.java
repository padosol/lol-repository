package com.mmrtr.lol.domain.summoner.domain;

import com.mmrtr.lol.domain.summoner.domain.vo.GameIdentity;
import com.mmrtr.lol.domain.summoner.domain.vo.Region;
import com.mmrtr.lol.domain.summoner.domain.vo.RevisionInfo;
import com.mmrtr.lol.domain.summoner.domain.vo.StatusInfo;
import com.mmrtr.lol.riot.dto.account.AccountDto;
import com.mmrtr.lol.riot.dto.league.LeagueEntryDTO;
import com.mmrtr.lol.riot.dto.summoner.SummonerDTO;
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
    private Region region;
    private StatusInfo statusInfo;
    private RevisionInfo revisionInfo;
    private Set<LeagueEntryDTO> leagueEntryDTOS;

    public static Summoner of(AccountDto accountDto,
                              SummonerDTO summonerDTO,
                              Region region,
                              Set<LeagueEntryDTO> leagueEntryDTOS) {
        return Summoner.builder()
                .puuid(accountDto.getPuuid())
                .gameIdentity(new GameIdentity(accountDto.getGameName(), accountDto.getTagLine()))
                .region(region)
                .statusInfo(new StatusInfo(summonerDTO.getProfileIconId(), summonerDTO.getSummonerLevel()))
                .revisionInfo(new RevisionInfo(
                        LocalDateTime.ofInstant(
                                Instant.ofEpochSecond(summonerDTO.getRevisionDate()), ZoneId.systemDefault()
                        ),
                        LocalDateTime.ofInstant(
                                Instant.ofEpochSecond(summonerDTO.getRevisionDate()), ZoneId.systemDefault()
                        )
                ))
                .leagueEntryDTOS(leagueEntryDTOS)
                .build();
    }
}
