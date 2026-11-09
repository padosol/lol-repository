package com.mmrtr.lol.domain.summoner.application;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.application.port.SummonerApiPort;
import com.mmrtr.lol.domain.summoner.application.usecase.SaveSummonerDataUseCase;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermitDeniedException;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerService {

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

    private RiotPermits acquire(String platform, RiotCallPriority priority, Map<RiotMethod, Integer> cost) {
        return riotPermitPort.tryAcquire(platform, priority, cost)
                .orElseThrow(() -> new RiotPermitDeniedException(platform, priority));
    }
}
