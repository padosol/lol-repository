package com.mmrtr.lol.domain.summoner.application;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.application.port.SummonerApiPort;
import com.mmrtr.lol.domain.summoner.application.usecase.SaveSummonerDataUseCase;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerService {

    private static final int BACKGROUND_ATTEMPTS = 10;
    private static final Duration BACKGROUND_PAUSE = Duration.ofSeconds(1);

    private final SummonerApiPort summonerApiPort;
    private final SaveSummonerDataUseCase saveSummonerDataUseCase;
    private final RiotPermitPort riotPermitPort;
    private final Executor requestExecutor;

    public Summoner getSummonerInfoV2(String regionType, String gameName, String tagLine) {
        try {
            RiotPermits permits = acquire(regionType, RiotCallPriority.USER, SummonerApiPort.BY_RIOT_ID_COST);
            Summoner summoner = summonerApiPort
                    .fetchSummonerByRiotId(gameName, tagLine, regionType, permits, requestExecutor)
                    .join();

            log.debug("getSummonerInfoV2 region type {} and gameName {}", regionType, gameName);
            summoner.initSummoner();
            saveSummonerDataUseCase.execute(summoner);

            return summoner;

        } catch (CoreException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Unexpected error: {}", e.getMessage());
            throw new CoreException(ErrorType.DEFAULT_ERROR, "유저 정보 조회 중 오류가 발생했습니다.");
        }
    }

    /** priority 로 Riot 호출 허가({@link SummonerApiPort#BY_PUUID_COST})를 받는다. HTTP 는 USER, 스케줄러는 BACKGROUND. */
    public Summoner getSummonerByPuuid(String regionType, String puuid, RiotCallPriority priority) {
        try {
            RiotPermits permits = acquire(regionType, priority, SummonerApiPort.BY_PUUID_COST);
            Summoner summoner = summonerApiPort
                    .fetchSummonerByPuuid(puuid, regionType, permits, requestExecutor)
                    .join();

            log.debug("getSummonerByPuuid region type {} and puuid {}", regionType, puuid);
            summoner.initSummoner();
            saveSummonerDataUseCase.execute(summoner);

            return summoner;

        } catch (CoreException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Unexpected error: {}", e.getMessage());
            throw new CoreException(ErrorType.DEFAULT_ERROR, "유저 정보 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * USER(HTTP 요청)는 한 번 묻고 거절이면 429 와 같은 응답({@code TOO_MANY_REQUESTS})을 던진다.
     * BACKGROUND(스케줄러)는 1초 간격으로 10번까지 다시 묻고, 그래도 거절이면 호출하는 쪽이 그 puuid 를 건너뛰게 던진다.
     */
    private RiotPermits acquire(String platform, RiotCallPriority priority, Map<RiotMethod, Integer> cost) {
        Optional<RiotPermits> granted = priority == RiotCallPriority.BACKGROUND
                ? riotPermitPort.acquireWithRetry(platform, priority, cost, BACKGROUND_ATTEMPTS, BACKGROUND_PAUSE)
                : riotPermitPort.tryAcquire(platform, priority, cost);
        return granted.orElseThrow(() -> new CoreException(ErrorType.TOO_MANY_REQUESTS, "잠시 후 다시 시도해주세요."));
    }
}
