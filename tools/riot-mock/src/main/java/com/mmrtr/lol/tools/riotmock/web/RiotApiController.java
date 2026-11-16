package com.mmrtr.lol.tools.riotmock.web;

import com.mmrtr.lol.tools.riotmock.world.LabWorld;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Riot 엔드포인트. 한도 판정은 {@link RiotGateFilter} 가 끝낸 뒤라 여기서는 데이터만 준다. */
@RestController
public class RiotApiController {

    static final String NOT_FOUND_BODY = "{\"status\":{\"message\":\"Data not found\",\"status_code\":404}}";
    // 2001-09-09 ~ 3000-01-01 (초). 밀리초 변환이 넘치지 않게 startTime 을 이 범위로 맞춘다.
    private static final long MIN_START_TIME_SEC = 0;
    private static final long MAX_START_TIME_SEC = 32_503_680_000L;

    private final LabWorld world;

    public RiotApiController(LabWorld world) {
        this.world = world;
    }

    @GetMapping(RiotEndpoint.Paths.ACCOUNT_BY_RIOT_ID)
    public ResponseEntity<?> accountByRiotId(@PathVariable String gameName, @PathVariable String tagLine) {
        return respond(world.accountByRiotId(gameName, tagLine));
    }

    @GetMapping(RiotEndpoint.Paths.ACCOUNT_BY_PUUID)
    public ResponseEntity<?> accountByPuuid(@PathVariable String puuid) {
        return respond(world.accountByPuuid(puuid));
    }

    @GetMapping(RiotEndpoint.Paths.SUMMONER_BY_PUUID)
    public ResponseEntity<?> summonerByPuuid(@PathVariable String puuid) {
        return respond(world.summonerByPuuid(puuid));
    }

    @GetMapping(RiotEndpoint.Paths.LEAGUE_ENTRIES_BY_PUUID)
    public ResponseEntity<?> leagueEntries(@PathVariable String puuid) {
        return respond(world.leagueEntriesByPuuid(puuid));
    }

    @GetMapping(RiotEndpoint.Paths.MATCH_IDS_BY_PUUID)
    public ResponseEntity<?> matchIds(@PathVariable String puuid,
                                      @RequestParam(required = false) Long startTime,
                                      @RequestParam(required = false) Integer start,
                                      @RequestParam(required = false) Integer count) {
        Long clamped = startTime == null ? null : Math.max(MIN_START_TIME_SEC, Math.min(MAX_START_TIME_SEC, startTime));
        return respond(world.matchIds(puuid, clamped, start, count));
    }

    @GetMapping(RiotEndpoint.Paths.MATCH)
    public ResponseEntity<?> match(@PathVariable String matchId) {
        return respond(world.match(matchId));
    }

    @GetMapping(RiotEndpoint.Paths.TIMELINE)
    public ResponseEntity<?> timeline(@PathVariable String matchId) {
        return respond(world.timeline(matchId));
    }

    private static ResponseEntity<?> respond(Optional<?> body) {
        if (body.isPresent()) {
            return ResponseEntity.ok(body.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(NOT_FOUND_BODY);
    }
}
