package com.mmrtr.lol.domain.match.application.port;

import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.support.riot.RiotPermits;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Riot 매치 조회. 메서드마다 그 method 의 허가 하나를 permits 에서 꺼내 쓴다. */
public interface MatchApiPort {

    CompletableFuture<List<String>> fetchMatchIdsByPuuid(
            String puuid, String platform, long startTime, int start, int count, RiotPermits permits,
            Executor executor);

    CompletableFuture<MatchDto> fetchMatchById(String matchId, String platform, RiotPermits permits, Executor executor);

    CompletableFuture<TimelineDto> fetchTimelineById(
            String matchId, String platform, RiotPermits permits, Executor executor);
}
