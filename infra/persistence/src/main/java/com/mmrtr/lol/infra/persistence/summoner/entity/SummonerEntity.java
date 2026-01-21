package com.mmrtr.lol.infra.persistence.summoner.entity;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.GameIdentity;
import com.mmrtr.lol.domain.summoner.domain.vo.RevisionInfo;
import com.mmrtr.lol.domain.summoner.domain.vo.StatusInfo;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;

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

    public static SummonerEntity fromDomain(Summoner summoner) {
        return SummonerEntity.builder()
                .puuid(summoner.getPuuid())
                .profileIconId(summoner.getStatusInfo().profileIconId())
                .revisionDate(summoner.getRevisionInfo().revisionDate())
                .summonerLevel(summoner.getStatusInfo().summonerLevel())
                .region(summoner.getPlatformId())
                .gameName(summoner.getGameIdentity().gameName())
                .tagLine(summoner.getGameIdentity().tagLine())
                .searchName((summoner.getGameIdentity().gameName().replace(" ", "") + "#" + summoner.getGameIdentity().tagLine()).toLowerCase())
                .revisionClickDate(LocalDateTime.now())
                .build();
    }

    public Summoner toDomain() {
        return Summoner.builder()
                .puuid(this.puuid)
                .gameIdentity(new GameIdentity(this.gameName, this.tagLine))
                .platformId(this.region)
                .statusInfo(new StatusInfo(this.profileIconId, this.summonerLevel))
                .revisionInfo(new RevisionInfo(this.revisionDate, this.revisionClickDate))
                .leagueInfos(new HashSet<>())
                .build();
    }

    public void initRevisionDate() {
        LocalDateTime revisionDateTime = LocalDateTime.of(2000, 1, 1, 1, 1, 1);
        this.revisionDate = revisionDateTime;
        this.revisionClickDate = revisionDateTime;
    }
}
