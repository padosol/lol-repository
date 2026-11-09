package com.mmrtr.lol.support.riot;

/**
 * Riot API 호출 종류. methodName 은 한도 학습 키로 쓰이므로 바꾸지 않는다.
 */
public enum RiotMethod {
    ACCOUNT_BY_RIOT_ID("account-v1.by-riot-id", Routing.REGIONAL),
    ACCOUNT_BY_PUUID("account-v1.by-puuid", Routing.REGIONAL),
    SUMMONER_BY_PUUID("summoner-v4.by-puuid", Routing.PLATFORM),
    LEAGUE_ENTRIES_BY_PUUID("league-v4.entries-by-puuid", Routing.PLATFORM),
    LEAGUE_ENTRIES("league-v4.entries", Routing.PLATFORM),
    CHALLENGER_LEAGUE("league-v4.challengerleagues", Routing.PLATFORM),
    GRANDMASTER_LEAGUE("league-v4.grandmasterleagues", Routing.PLATFORM),
    MATCH_IDS_BY_PUUID("match-v5.ids-by-puuid", Routing.REGIONAL),
    MATCH("match-v5.match", Routing.REGIONAL),
    MATCH_TIMELINE("match-v5.timeline", Routing.REGIONAL),
    CHAMPION_ROTATION("champion-v3.rotations", Routing.PLATFORM),
    ACTIVE_GAME("spectator-v5.active-games", Routing.PLATFORM);

    /** 호출이 향하는 Riot 호스트 종류. */
    public enum Routing {
        REGIONAL,
        PLATFORM
    }

    private final String methodName;
    private final Routing routing;

    RiotMethod(String methodName, Routing routing) {
        this.methodName = methodName;
        this.routing = routing;
    }

    public String methodName() {
        return methodName;
    }

    public Routing routing() {
        return routing;
    }
}
