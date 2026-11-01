package com.mmrtr.lol.tools.riotmock.web;

import java.util.Optional;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** 가짜 서버가 받는 Riot 엔드포인트. key 는 앱의 RiotRequestAttributes.METHOD 값과 같다. 컨트롤러 매핑과 1:1 이다. */
public enum RiotEndpoint {

    ACCOUNT_BY_RIOT_ID("account-v1.by-riot-id", Paths.ACCOUNT_BY_RIOT_ID),
    ACCOUNT_BY_PUUID("account-v1.by-puuid", Paths.ACCOUNT_BY_PUUID),
    SUMMONER_BY_PUUID("summoner-v4.by-puuid", Paths.SUMMONER_BY_PUUID),
    LEAGUE_ENTRIES_BY_PUUID("league-v4.entries-by-puuid", Paths.LEAGUE_ENTRIES_BY_PUUID),
    MATCH_IDS_BY_PUUID("match-v5.ids-by-puuid", Paths.MATCH_IDS_BY_PUUID),
    MATCH("match-v5.match", Paths.MATCH),
    TIMELINE("match-v5.timeline", Paths.TIMELINE);

    /** 한도 버킷과 지표에서 모르는 경로를 가리키는 키. */
    public static final String UNKNOWN_KEY = "unknown";

    private final String key;
    private final PathPattern pattern;

    RiotEndpoint(String key, String path) {
        this.key = key;
        this.pattern = new PathPatternParser().parse(path);
    }

    public String key() {
        return key;
    }

    public static boolean isKnownKey(String key) {
        for (RiotEndpoint endpoint : values()) {
            if (endpoint.key.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** 인코딩된 원본 경로(%20 등 그대로)를 매칭한다. */
    public static Optional<Match> match(String rawPath) {
        PathContainer path = PathContainer.parsePath(rawPath);
        for (RiotEndpoint endpoint : values()) {
            PathPattern.PathMatchInfo info = endpoint.pattern.matchAndExtract(path);
            if (info != null) {
                return Optional.of(new Match(endpoint, info.getUriVariables().get("routing")));
            }
        }
        return Optional.empty();
    }

    public record Match(RiotEndpoint endpoint, String routing) {
    }

    /** 컨트롤러 매핑과 열거형이 같은 문자열을 쓰도록 하는 상수. */
    public static final class Paths {
        public static final String ACCOUNT_BY_RIOT_ID =
                "/{routing:[a-z0-9]+}/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}";
        public static final String ACCOUNT_BY_PUUID = "/{routing:[a-z0-9]+}/riot/account/v1/accounts/by-puuid/{puuid}";
        public static final String SUMMONER_BY_PUUID = "/{routing:[a-z0-9]+}/lol/summoner/v4/summoners/by-puuid/{puuid}";
        public static final String LEAGUE_ENTRIES_BY_PUUID = "/{routing:[a-z0-9]+}/lol/league/v4/entries/by-puuid/{puuid}";
        public static final String MATCH_IDS_BY_PUUID = "/{routing:[a-z0-9]+}/lol/match/v5/matches/by-puuid/{puuid}/ids";
        public static final String MATCH = "/{routing:[a-z0-9]+}/lol/match/v5/matches/{matchId}";
        public static final String TIMELINE = "/{routing:[a-z0-9]+}/lol/match/v5/matches/{matchId}/timeline";

        private Paths() {
        }
    }
}
