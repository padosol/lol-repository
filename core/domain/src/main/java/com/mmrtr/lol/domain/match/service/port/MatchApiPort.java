package com.mmrtr.lol.domain.match.service.port;

import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface MatchApiPort {

    CompletableFuture<List<String>> fetchMatchIdsByPuuid(
            String puuid, String platform, Executor executor);

    CompletableFuture<List<String>> fetchMatchIdsByPuuid(
            String puuid, String platform, long startTime, int start, int count, Executor executor);

    CompletableFuture<MatchDto> fetchMatchById(String matchId, String platform, Executor executor);

    CompletableFuture<TimelineDto> fetchTimelineById(String matchId, String platform, Executor executor);
}
