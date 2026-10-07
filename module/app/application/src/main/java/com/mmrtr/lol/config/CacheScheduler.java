package com.mmrtr.lol.config;

import com.mmrtr.lol.domain.champion.application.usecase.ChampionRotationQueryUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheScheduler {

    private final ChampionRotationQueryUseCase championRotationQueryUseCase;

    /**
     * 챔피언 로테이션은 매주 화요일 패치와 함께 갱신되므로 화요일 00:10 KST 에 캐시를 비운다.
     * 이후 첫 요청이 Riot 에서 새 로테이션을 받아 다시 캐싱한다. (기본 타임존은 {@link TimeZoneConfig})
     */
    @Scheduled(cron = "0 10 0 * * TUE")
    public void evictChampionRotationCache() {
        championRotationQueryUseCase.evictChampionRotations();
        log.info("Champion rotation cache has been evicted.");
    }
}
