package com.mmrtr.lol.tools.riotmock.world;

/** Riot 응답과 같은 JSON 필드 이름을 가진 계정·소환사·리그 레코드. */
public final class RiotRecords {

    private RiotRecords() {
    }

    public record AccountRecord(String puuid, String gameName, String tagLine) {
    }

    public record SummonerRecord(String puuid, int profileIconId, long revisionDate, long summonerLevel) {
    }

    public record LeagueEntryRecord(String leagueId, String puuid, String queueType, String tier, String rank,
                                    int leaguePoints, int wins, int losses, boolean hotStreak, boolean veteran,
                                    boolean freshBlood, boolean inactive) {
    }
}
