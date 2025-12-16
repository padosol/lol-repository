package com.mmrtr.lol.controller.summoner.dto.response;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.riot.dto.league.LeagueEntryDto;
import lombok.*;

import java.time.LocalDateTime;
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
    private long summonerLevel;
    private LocalDateTime revisionDate;

    private Set<LeagueEntryDto> leagueEntryDTOS;

//    public static SummonerResponse of(AccountDto accountDto, SummonerDTO summonerDTO, Set<LeagueEntryDTO> leagueEntryDTOS) {
//        return SummonerResponse.builder()
//                .puuid(accountDto.getPuuid())
//                .gameName(accountDto.getGameName())
//                .tagLine(accountDto.getTagLine())
//                .id(summonerDTO.getId())
//                .accountId(summonerDTO.getAccountId())
//                .profileIconId(summonerDTO.getProfileIconId())
//                .revisionDate(summonerDTO.getRevisionDate())
//                .summonerLevel(summonerDTO.getSummonerLevel())
//                .leagueEntryDTOS(leagueEntryDTOS)
//                .build();
//    }

    public static SummonerResponse of(Summoner summoner) {
        return SummonerResponse.builder()
                .puuid(summoner.getPuuid())
                .gameName(summoner.getGameIdentity().gameName())
                .tagLine(summoner.getGameIdentity().tagLine())
                .profileIconId(summoner.getStatusInfo().profileIconId())
                .revisionDate(summoner.getRevisionInfo().revisionDate())
                .summonerLevel(summoner.getStatusInfo().summonerLevel())
                .leagueEntryDTOS(summoner.getLeagueEntryDtos())
                .build();
    }
}
