package com.mmrtr.lol.domain.match.application.port;

import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermits;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Riot 매치 조회. 메서드마다 그 method 의 허가 하나를 permits 에서 꺼내 쓴다. */
public interface MatchApiPort {

    /** 매치 하나(match + timeline)를 받는 데 필요한 허가. */
    Map<RiotMethod, Integer> MATCH_COST = Map.of(RiotMethod.MATCH, 1, RiotMethod.MATCH_TIMELINE, 1);

    CompletableFuture<List<String>> fetchMatchIdsByPuuid(
            String puuid, String platform, long startTime, int start, int count, RiotPermits permits,
            Executor executor);

    CompletableFuture<MatchDto> fetchMatchById(String matchId, String platform, RiotPermits permits, Executor executor);

    CompletableFuture<TimelineDto> fetchTimelineById(
            String matchId, String platform, RiotPermits permits, Executor executor);
}
