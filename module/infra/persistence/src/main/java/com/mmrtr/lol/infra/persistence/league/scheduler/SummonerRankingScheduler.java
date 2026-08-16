package com.mmrtr.lol.infra.persistence.league.scheduler;

import com.mmrtr.lol.domain.league.application.usecase.TriggerSummonerRankingCalculationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummonerRankingScheduler implements TriggerSummonerRankingCalculationUseCase {

    private final SummonerRankingCalculationService summonerRankingCalculationService;

    private static final List<String> QUEUE_TYPES = List.of(
            "RANKED_SOLO_5x5"
    );

    /**
     * 티어 커트라인(챌린저/그랜드마스터 컷)을 갱신하는 시간대. 00:00(포함) ~ 02:00(제외), KST 기준.
     * 이 시간대 밖에서 실행된 스케줄은 랭킹만 교체하고 커트라인은 직전 값을 유지한다.
     */
    private static final LocalTime TIER_CUTOFF_WINDOW_START = LocalTime.MIDNIGHT;
    private static final LocalTime TIER_CUTOFF_WINDOW_END = LocalTime.of(2, 0);

    @Override
    @Scheduled(fixedDelay = 7200000, initialDelay = 60000)
    public void execute() {
        execute(LocalTime.now());
    }

    void execute(LocalTime executedAt) {
        boolean updateTierCutoff = isTierCutoffWindow(executedAt);

        log.info("소환사 랭킹 스케줄링 시작 (실행 시각 {}시, 티어 커트라인 갱신 {})",
                executedAt.getHour(), updateTierCutoff ? "포함" : "제외");

        for (String queue : QUEUE_TYPES) {
            summonerRankingCalculationService.processQueueRanking(queue, updateTierCutoff);
        }

        log.info("소환사 랭킹 스케줄링 완료");
    }

    static boolean isTierCutoffWindow(LocalTime executedAt) {
        return !executedAt.isBefore(TIER_CUTOFF_WINDOW_START)
                && executedAt.isBefore(TIER_CUTOFF_WINDOW_END);
    }
}
