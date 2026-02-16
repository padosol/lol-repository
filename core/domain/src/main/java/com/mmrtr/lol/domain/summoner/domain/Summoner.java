package com.mmrtr.lol.domain.summoner.domain;

import com.mmrtr.lol.domain.summoner.domain.vo.GameIdentity;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;
import com.mmrtr.lol.domain.summoner.domain.vo.RevisionInfo;
import com.mmrtr.lol.domain.summoner.domain.vo.StatusInfo;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Summoner {
    private String puuid;
    private GameIdentity gameIdentity;
    private String platformId;
    private StatusInfo statusInfo;
    private RevisionInfo revisionInfo;
    private Set<LeagueInfo> leagueInfos;

    public static Summoner create(
            String puuid,
            String gameName,
            String tagLine,
            String platformId,
            int profileIconId,
            long summonerLevel,
            long revisionDateMillis,
            Set<LeagueInfo> leagueInfos) {

        LocalDateTime revisionDate = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(revisionDateMillis), ZoneId.systemDefault()
        );

        return Summoner.builder()
                .puuid(puuid)
                .gameIdentity(new GameIdentity(gameName, tagLine))
                .platformId(platformId)
                .statusInfo(new StatusInfo(profileIconId, summonerLevel))
                .revisionInfo(new RevisionInfo(revisionDate, revisionDate))
                .leagueInfos(leagueInfos)
                .build();
    }

    public void resetClickDate() {
        revisionInfo = new RevisionInfo(
                revisionInfo.revisionDate(), LocalDateTime.now());
    }

    public void initSummoner() {
        revisionInfo = new RevisionInfo(
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0)
        );
    }
}
