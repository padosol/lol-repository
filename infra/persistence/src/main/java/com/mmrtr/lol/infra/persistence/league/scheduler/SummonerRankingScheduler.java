package com.mmrtr.lol.infra.persistence.league.scheduler;

import com.mmrtr.lol.domain.league.service.usecase.TriggerSummonerRankingCalculationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummonerRankingScheduler implements TriggerSummonerRankingCalculationUseCase {

    private final SummonerRankingCalculationService summonerRankingCalculationService;

    private static final List<String> QUEUE_TYPES = List.of(
            "RANKED_SOLO_5x5",
            "RANKED_FLEX_SR"
    );

    @Override
    @Scheduled(fixedDelay = 7200000, initialDelay = 60000)
    public void execute() {
        log.info("소환사 랭킹 스케줄링 시작");

        for (String queue : QUEUE_TYPES) {
            summonerRankingCalculationService.processQueueRanking(queue);
        }

        log.info("소환사 랭킹 스케줄링 완료");
    }
}
