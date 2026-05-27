package com.mmrtr.lol.domain.league.application;

import com.mmrtr.lol.common.type.Division;
import com.mmrtr.lol.domain.league.application.port.LeagueApiPort;
import com.mmrtr.lol.domain.league.application.port.SummonerCollectionPublishPort;
import com.mmrtr.lol.domain.league.application.usecase.CollectTierDataUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TierCollectionService implements CollectTierDataUseCase {

    private final LeagueApiPort leagueApiPort;
    private final SummonerCollectionPublishPort publishPort;

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
                Set<LeagueApiPort.LeagueEntry> entries =
                        leagueApiPort.getLeagueEntries(queue, tier, division.name(), page, platformName);

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
                publishPort.publishForRenewal(puuids, platformName);

                page++;
            }
        }

        log.info("[티어 수집 완료] tier={}, platform={}, 총 수집={}명", tier, platformName, totalCollected);
    }
}
