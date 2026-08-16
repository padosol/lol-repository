package com.mmrtr.lol.infra.persistence.summoner.entity;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.GameIdentity;
import com.mmrtr.lol.domain.summoner.domain.vo.RevisionInfo;
import com.mmrtr.lol.domain.summoner.domain.vo.StatusInfo;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;
import java.util.HashSet;

@Getter
@Entity
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "summoner",
        indexes = {
                @Index(name = "idx_summoner_platform_id_search_name", columnList = "platform_id, search_name")
        }
)
public class SummonerEntity {

    @Id
    @Comment("소환사 고유 식별자")
    private String puuid;
    @Comment("프로필 아이콘 ID")
    private int profileIconId;
    @Comment("소환사 레벨")
    private long summonerLevel;
    @Comment("게임 닉네임")
    private String gameName;
    @Comment("태그라인")
    private String tagLine;
    @Comment("플랫폼 ID")
    private String platformId;
    @Comment("검색용 이름 (소문자)")
    private String searchName;
    @Comment("정보 갱신 일시")
    private LocalDateTime revisionDate;
    @Comment("최근 RIOT API 호출 일시")
    private LocalDateTime lastRiotCallDate;

    public static SummonerEntity fromDomain(Summoner summoner) {
        return SummonerEntity.builder()
                .puuid(summoner.getPuuid())
                .profileIconId(summoner.getStatusInfo().profileIconId())
                .revisionDate(summoner.getRevisionInfo().revisionDate())
                .summonerLevel(summoner.getStatusInfo().summonerLevel())
                .platformId(summoner.getPlatformId())
                .gameName(summoner.getGameIdentity().gameName())
                .tagLine(summoner.getGameIdentity().tagLine())
                .searchName((summoner.getGameIdentity().gameName().replace(" ", "") + "#" + summoner.getGameIdentity().tagLine()).toLowerCase())
                .lastRiotCallDate(summoner.getRevisionInfo().lastRiotCallDate())
                .build();
    }

    public Summoner toDomain() {
        return Summoner.builder()
                .puuid(this.puuid)
                .gameIdentity(new GameIdentity(this.gameName, this.tagLine))
                .platformId(this.platformId)
                .statusInfo(new StatusInfo(this.profileIconId, this.summonerLevel))
                .revisionInfo(new RevisionInfo(this.revisionDate, this.lastRiotCallDate))
                .leagueInfos(new HashSet<>())
                .build();
    }

    public void updateLastRiotCallDate() {
        this.lastRiotCallDate = LocalDateTime.now();
    }

    public void initRevisionDate() {
        LocalDateTime revisionDateTime = LocalDateTime.of(2000, 1, 1, 1, 1, 1);
        this.revisionDate = revisionDateTime;
        this.lastRiotCallDate = revisionDateTime;
    }
}
