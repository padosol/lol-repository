package com.mmrtr.lol.domain.spectator.application;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.spectator.application.port.ActiveGameCachePort;
import com.mmrtr.lol.domain.spectator.application.port.SpectatorApiPort;
import com.mmrtr.lol.domain.spectator.application.port.SpectatorTargetPort;
import com.mmrtr.lol.domain.spectator.application.usecase.ActiveGameQueryUseCase;
import com.mmrtr.lol.domain.spectator.domain.ActiveGame;
import com.mmrtr.lol.domain.spectator.domain.SpectatorTarget;
import com.mmrtr.lol.domain.spectator.readmodel.ActiveGameReadModel;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * lol-server {@code SpectatorService}·{@code SpectatorFinder} 의 이전. 캐시 흐름(게임 캐시 → 게임 없음 캐시 → Riot)과
 * 무효화 기준(게임 시작 뒤 소환사 갱신)이 같다. lol-server 는 저장된 platform_id(NA1 …)를 플랫폼 이름 자리에 넘겨
 * KR 외 지역에서 조회가 실패했다 — 여기서는 platform_id 로 플랫폼을 찾는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActiveGameQueryService implements ActiveGameQueryUseCase {

    private final SpectatorTargetPort spectatorTargetPort;
    private final ActiveGameCachePort activeGameCachePort;
    private final SpectatorApiPort spectatorApiPort;
    private final Executor requestExecutor;

    @Override
    public Optional<ActiveGameReadModel> getActiveGame(String puuid) {
        SpectatorTarget target = spectatorTargetPort.findTarget(puuid)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_PUUID));
        Platform platform = Platform.valueOfPlatformId(target.platformId());
        if (platform == null) {
            throw new CoreException(ErrorType.UNKNOWN_SUMMONER_PLATFORM);
        }
        String platformId = platform.getPlatformId();

        Optional<ActiveGameReadModel> cached = activeGameCachePort.findGame(platformId, puuid);
        if (cached.isPresent()) {
            if (!target.renewedAfter(cached.get().gameStartTime())) {
                return cached;
            }
            activeGameCachePort.evictGame(platformId, cached.get());
        }
        if (activeGameCachePort.isNotInGame(platformId, puuid)) {
            return Optional.empty();
        }

        Optional<ActiveGame> game;
        try {
            game = spectatorApiPort.fetchActiveGameByPuuid(puuid, platform.name(), requestExecutor).join();
        } catch (RuntimeException e) {
            // lol-server 와 같이 "게임 없음"으로 응답하되 캐싱하지 않는다 — 다음 조회가 다시 Riot 을 본다
            log.warn("관전 조회 실패: {}", e.getMessage());
            return Optional.empty();
        }
        if (game.isEmpty()) {
            activeGameCachePort.saveNotInGame(platformId, puuid);
            return Optional.empty();
        }
        ActiveGameReadModel readModel = ActiveGameReadModel.from(game.get());
        activeGameCachePort.saveGame(platformId, readModel);
        return Optional.of(readModel);
    }
}
