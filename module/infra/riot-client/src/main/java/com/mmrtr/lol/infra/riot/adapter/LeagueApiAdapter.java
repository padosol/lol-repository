package com.mmrtr.lol.infra.riot.adapter;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.common.type.Tier;
import com.mmrtr.lol.domain.league.application.port.LeagueApiPort;
import com.mmrtr.lol.infra.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.infra.riot.dto.league.LeagueListDto;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeagueApiAdapter implements LeagueApiPort {

    private final RiotApiService riotApiService;
    private final Executor requestExecutor;

    @Override
    public Set<LeagueEntry> getLeagueEntries(String queue, String tier, String division, int page, String platformName) {
        Platform platform = Platform.valueOfName(platformName);
        Set<LeagueEntryDto> entries = riotApiService.getLeagueEntries(queue, tier, division, page, platform);
        if (entries == null || entries.isEmpty()) {
            return Set.of();
        }
        return entries.stream()
                .map(dto -> new LeagueEntry(
                        dto.getPuuid(),
                        dto.getLeaguePoints(),
                        dto.getRank(),
                        dto.getWins(),
                        dto.getLosses(),
                        dto.isVeteran(),
                        dto.isInactive(),
                        dto.isFreshBlood(),
                        dto.isHotStreak()
                ))
                .collect(Collectors.toSet());
    }

    @Override
    public Map<Tier, List<LeagueEntry>> getApexEntries(String queue, String platformName) {
        Platform platform = Platform.valueOfName(platformName);
        log.debug("getApexEntries queue={} platform={}", queue, platformName);

        CompletableFuture<LeagueListDto> challengerFuture =
                riotApiService.getApexLeague("challengerleagues", queue, platform, requestExecutor);
        CompletableFuture<LeagueListDto> grandmasterFuture =
                riotApiService.getApexLeague("grandmasterleagues", queue, platform, requestExecutor);

        CompletableFuture.allOf(challengerFuture, grandmasterFuture).join();

        return Map.of(
                Tier.CHALLENGER, toEntries(challengerFuture.join()),
                Tier.GRANDMASTER, toEntries(grandmasterFuture.join())
        );
    }

    private List<LeagueEntry> toEntries(LeagueListDto dto) {
        if (dto == null || dto.getEntries() == null) {
            return List.of();
        }
        return dto.getEntries().stream()
                .map(item -> new LeagueEntry(
                        item.getPuuid(),
                        item.getLeaguePoints(),
                        item.getRank(),
                        item.getWins(),
                        item.getLosses(),
                        item.isVeteran(),
                        item.isInactive(),
                        item.isFreshBlood(),
                        item.isHotStreak()
                ))
                .toList();
    }
}
