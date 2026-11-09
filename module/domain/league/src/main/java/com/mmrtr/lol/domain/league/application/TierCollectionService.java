package com.mmrtr.lol.domain.league.application;

import com.mmrtr.lol.common.type.Division;
import com.mmrtr.lol.domain.league.application.port.LeagueApiPort;
import com.mmrtr.lol.domain.league.application.port.SummonerCollectionPublishPort;
import com.mmrtr.lol.domain.league.application.usecase.CollectTierDataUseCase;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TierCollectionService implements CollectTierDataUseCase {

    private static final int PERMIT_ATTEMPTS = 10;
    private static final Duration PERMIT_PAUSE = Duration.ofSeconds(1);

    private final LeagueApiPort leagueApiPort;
    private final SummonerCollectionPublishPort publishPort;
    private final RiotPermitPort riotPermitPort;

    /** 스케줄 작업이라 Riot 호출은 BACKGROUND 허가로 페이지마다 받는다. 거절되면 1초 간격으로 10번까지 다시 묻고, 그래도 안 되면 경고를 남기고 수집을 멈춘다. */
    @Async("requestExecutor")
    @Override
    public void execute(String platformName) {
        String queue = "RANKED_SOLO_5x5";
        String tier = "EMERALD";
        int totalCollected = 0;

        log.info("[티어 수집 시작] tier={}, platform={}", tier, platformName);

        for (Division division : Division.values()) {
            int page = 1;
            int divisionCollected = 0;

            while (true) {
                Optional<RiotPermits> granted = riotPermitPort.acquireWithRetry(
                        platformName, RiotCallPriority.BACKGROUND, Map.of(RiotMethod.LEAGUE_ENTRIES, 1),
                        PERMIT_ATTEMPTS, PERMIT_PAUSE);
                if (granted.isEmpty()) {
                    log.warn("[티어 수집 중단] Riot 허가를 받지 못했다. tier={}, division={}, page={}, 전체 누적={}",
                            tier, division, page, totalCollected);
                    return;
                }
                RiotPermits permits = granted.get();
                Set<LeagueApiPort.LeagueEntry> entries =
                        leagueApiPort.getLeagueEntries(queue, tier, division.name(), page, platformName, permits);

                if (entries.isEmpty()) {
                    log.info("[디비전 수집 완료] division={}, 총 {}명, 마지막 페이지={}",
                            division, divisionCollected, page - 1);
                    break;
                }

                divisionCollected += entries.size();
                totalCollected += entries.size();

                log.info("[수집 진행] tier={}, division={}, page={}, 페이지 수집={}, 디비전 누적={}, 전체 누적={}",
                        tier, division, page, entries.size(), divisionCollected, totalCollected);

                List<String> puuids = entries.stream()
                        .map(LeagueApiPort.LeagueEntry::puuid)
                        .toList();
                publishPort.publishForCollection(puuids, platformName);

                page++;
            }
        }

        log.info("[티어 수집 완료] tier={}, platform={}, 총 수집={}명", tier, platformName, totalCollected);
    }
}
