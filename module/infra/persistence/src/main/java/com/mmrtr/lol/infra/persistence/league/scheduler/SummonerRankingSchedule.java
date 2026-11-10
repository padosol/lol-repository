package com.mmrtr.lol.infra.persistence.league.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 소환사 랭킹 계산을 앱 기동 1분 뒤, 그 뒤 2시간마다 돌린다.
 * {@code lol.ranking.scheduler.enabled=false} 면 이 빈이 뜨지 않아 주기 실행만 멈춘다 (관리자 API 수동 실행은 된다).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lol.ranking.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SummonerRankingSchedule {

    private final SummonerRankingScheduler summonerRankingScheduler;

    @Scheduled(fixedDelay = 7200000, initialDelay = 60000)
    public void run() {
        summonerRankingScheduler.execute();
    }
}
