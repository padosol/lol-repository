package com.mmrtr.lol.infra.riot.adapter;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchApiAdapter implements MatchApiPort {

    private final RiotApiService riotApiService;

    @Override
    public CompletableFuture<List<String>> fetchMatchIdsByPuuid(
            String puuid, String platformName, long startTime, int start, int count, RiotPermits permits,
            Executor executor) {
        Platform platform = Platform.valueOfName(platformName);
        return riotApiService.getMatchListByPuuid(puuid, platform, startTime, start, count,
                permits.take(RiotMethod.MATCH_IDS_BY_PUUID), executor);
    }

    @Override
    public CompletableFuture<MatchDto> fetchMatchById(
            String matchId, String platformName, RiotPermits permits, Executor executor) {
        Platform platform = Platform.valueOfName(platformName);
        return riotApiService.getMatchById(matchId, platform, permits.take(RiotMethod.MATCH), executor);
    }

    @Override
    public CompletableFuture<TimelineDto> fetchTimelineById(
            String matchId, String platformName, RiotPermits permits, Executor executor) {
        Platform platform = Platform.valueOfName(platformName);
        return riotApiService.getTimelineById(matchId, platform, permits.take(RiotMethod.MATCH_TIMELINE), executor);
    }
}
