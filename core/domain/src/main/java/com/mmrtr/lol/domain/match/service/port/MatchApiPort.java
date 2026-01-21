package com.mmrtr.lol.domain.match.service.port;

import com.mmrtr.lol.domain.match.domain.Match;
import com.mmrtr.lol.domain.match.domain.MatchTimeline;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface MatchApiPort {

    CompletableFuture<List<String>> fetchMatchIdsByPuuid(
            String puuid, String platform, Executor executor);

    CompletableFuture<List<String>> fetchMatchIdsByPuuid(
            String puuid, String platform, long startTime, int start, int count, Executor executor);

    CompletableFuture<Match> fetchMatchById(String matchId, String platform, Executor executor);

    CompletableFuture<MatchTimeline> fetchTimelineById(String matchId, String platform, Executor executor);
}
