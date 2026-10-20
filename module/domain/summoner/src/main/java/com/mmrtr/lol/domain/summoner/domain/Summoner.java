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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Summoner {
    public static final Duration RENEWAL_INTERVAL = Duration.ofMinutes(2);

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
                .revisionInfo(new RevisionInfo(revisionDate, LocalDateTime.now()))
                .leagueInfos(leagueInfos)
                .build();
    }

    public void updateLastRiotCallDate() {
        revisionInfo = new RevisionInfo(
                revisionInfo.revisionDate(), LocalDateTime.now());
    }

    public void initSummoner() {
        revisionInfo = new RevisionInfo(
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0)
        );
    }

    /** 마지막 Riot API 호출 뒤 {@link #RENEWAL_INTERVAL} 이 지나야 다시 갱신할 수 있다 (lol-server {@code isRevision} 과 같다). */
    public boolean isRenewableAt(LocalDateTime now) {
        LocalDateTime lastRiotCallDate = revisionInfo != null ? revisionInfo.lastRiotCallDate() : null;
        return lastRiotCallDate == null || !lastRiotCallDate.plus(RENEWAL_INTERVAL).isAfter(now);
    }
}
