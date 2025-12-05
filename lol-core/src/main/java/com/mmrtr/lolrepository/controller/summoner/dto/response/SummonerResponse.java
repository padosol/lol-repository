package com.mmrtr.lolrepository.controller.summoner.dto.response;

import com.mmrtr.lolrepository.riot.dto.account.AccountDto;
import com.mmrtr.lolrepository.riot.dto.league.LeagueEntryDTO;
import com.mmrtr.lolrepository.riot.dto.summoner.SummonerDTO;
import lombok.*;

import java.util.Set;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SummonerResponse {
    private String puuid;
    private String gameName;
    private String tagLine;
    private String id;
    private String accountId;
    private int profileIconId;
    private long revisionDate;
    private long summonerLevel;

    private Set<LeagueEntryDTO> leagueEntryDTOS;

    public static SummonerResponse of(AccountDto accountDto, SummonerDTO summonerDTO, Set<LeagueEntryDTO> leagueEntryDTOS) {
        return SummonerResponse.builder()
                .puuid(accountDto.getPuuid())
                .gameName(accountDto.getGameName())
                .tagLine(accountDto.getTagLine())
                .id(summonerDTO.getId())
                .accountId(summonerDTO.getAccountId())
                .profileIconId(summonerDTO.getProfileIconId())
                .revisionDate(summonerDTO.getRevisionDate())
                .summonerLevel(summonerDTO.getSummonerLevel())
                .leagueEntryDTOS(leagueEntryDTOS)
                .build();
    }
}
