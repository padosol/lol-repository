package com.mmrtr.lol.domain.summoner.readmodel;

import com.mmrtr.lol.domain.summoner.domain.Summoner;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 소환사 상세. 필드·순서·날짜 형식이 lol-server {@code SummonerReadModel} 과 같다.
 *
 * @param platform                  lol-server 가 채우지 않아 항상 null — 응답 호환을 위해 남긴다
 * @param lastRevisionDateTime      Riot 정보 갱신 일시 ({@code yyyy-MM-dd HH:mm:ss})
 * @param lastRevisionClickDateTime 마지막 Riot API 호출 일시 ({@code yyyy-MM-dd HH:mm:ss})
 */
public record SummonerReadModel(
        int profileIconId,
        String puuid,
        long summonerLevel,
        String gameName,
        String tagLine,
        String platform,
        String lastRevisionDateTime,
        String lastRevisionClickDateTime
) {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static SummonerReadModel from(Summoner summoner) {
        return new SummonerReadModel(
                summoner.getStatusInfo().profileIconId(),
                summoner.getPuuid(),
                summoner.getStatusInfo().summonerLevel(),
                summoner.getGameIdentity().gameName(),
                summoner.getGameIdentity().tagLine(),
                null,
                format(summoner.getRevisionInfo().revisionDate()),
                format(summoner.getRevisionInfo().lastRiotCallDate())
        );
    }

    private static String format(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_FORMATTER) : null;
    }
}
