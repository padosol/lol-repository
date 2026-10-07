package com.mmrtr.lol.domain.champion.application;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.champion.application.port.ChampionRotationCachePort;
import com.mmrtr.lol.domain.champion.application.usecase.ChampionRotationQueryUseCase;
import com.mmrtr.lol.domain.champion.domain.ChampionRotation;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 공개 API 용 챔피언 로테이션 조회 (캐시 우선). lol-server {@code ChampionService} 에서 이전.
 * Riot 조회 자체는 {@link ChampionRotateService} 에 위임한다.
 *
 * <p>로테이션은 매주 화요일 갱신되지만 캐시 TTL(1시간)이 짧아 별도 evict 없이 그 안에 반영된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChampionRotationQueryService implements ChampionRotationQueryUseCase {

    private final ChampionRotateService championRotateService;
    private final ChampionRotationCachePort championRotationCachePort;

    @Override
    public ChampionRotation getChampionRotation(String platform) {
        if (Platform.valueOfName(platform) == null) {
            throw new CoreException(ErrorType.INVALID_INPUT, "지원하지 않는 platform: " + platform);
        }
        return championRotationCachePort.find(platform)
                .orElseGet(() -> {
                    ChampionRotation rotation = fetch(platform);
                    // 빈 로테이션도 캐싱한다. 캐시 어댑터가 빈 값에는 짧은 TTL(negative cache)을 적용해
                    // Riot rate limit 소모를 막으면서 복구를 빠르게 반영한다.
                    championRotationCachePort.save(platform, rotation);
                    return rotation;
                });
    }

    /** Riot 조회 실패는 빈 로테이션으로 응답한다 (lol-server 와 같은 동작). */
    private ChampionRotation fetch(String platform) {
        try {
            return championRotateService.getChampionRotate(platform);
        } catch (RuntimeException e) {
            log.warn("챔피언 로테이션 조회 실패 - platform: {}, cause: {}", platform, e.getMessage());
            return new ChampionRotation(List.of(), List.of(), 0);
        }
    }
}
