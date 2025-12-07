package com.mmrtr.lol.domain.summoner.domain;

import com.mmrtr.lol.domain.summoner.domain.vo.GameIdentity;
import com.mmrtr.lol.domain.summoner.domain.vo.Region;
import com.mmrtr.lol.domain.summoner.domain.vo.RevisionInfo;
import com.mmrtr.lol.domain.summoner.domain.vo.StatusInfo;
import com.mmrtr.lol.riot.dto.account.AccountDto;
import com.mmrtr.lol.riot.dto.league.LeagueEntryDTO;
import com.mmrtr.lol.riot.dto.summoner.SummonerDTO;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
public class Summoner {
    private String puuid;

    private GameIdentity gameIdentity;
//    private String searchName;

    private Region region;
//    private String region;

    private StatusInfo statusInfo;
//    private int profileIconId;
//    private long summonerLevel;

    private RevisionInfo revisionInfo;
//    private long revisionDate;
//    private LocalDateTime revisionClickDate;

    private Set<LeagueEntryDTO> leagueEntryDTOS;

    public static Summoner of(AccountDto accountDto, SummonerDTO summonerDTO, Set<LeagueEntryDTO> leagueEntryDTOS) {
        return null;
    }
}
