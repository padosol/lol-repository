package com.mmrtr.lol.controller.summoner.dto.response;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

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

    private Set<LeagueInfo> leagueInfos;

    public static SummonerResponse of(Summoner summoner) {
        return SummonerResponse.builder()
                .puuid(summoner.getPuuid())
                .gameName(summoner.getGameIdentity().gameName())
                .tagLine(summoner.getGameIdentity().tagLine())
                .profileIconId(summoner.getStatusInfo().profileIconId())
                .revisionDate(summoner.getRevisionInfo().revisionDate())
                .summonerLevel(summoner.getStatusInfo().summonerLevel())
                .leagueInfos(summoner.getLeagueInfos())
                .build();
    }
}
