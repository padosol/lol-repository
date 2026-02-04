package com.mmrtr.lol.domain.summoner.service;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.service.port.SummonerApiPort;
import com.mmrtr.lol.domain.summoner.service.usecase.SaveSummonerDataUseCase;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerService {

    private final SummonerApiPort summonerApiPort;
    private final SaveSummonerDataUseCase saveSummonerDataUseCase;
    private final Executor requestExecutor;

    @Transactional
    public Summoner getSummonerInfoV2(String regionType, String gameName, String tagLine) {
        try {
            Summoner summoner = summonerApiPort
                    .fetchSummonerByRiotId(gameName, tagLine, regionType, requestExecutor)
                    .join();

            log.info("getSummonerInfoV2 region type {} and gameName {}", regionType, gameName);
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

    @Transactional
    public Summoner getSummonerByPuuid(String regionType, String puuid) {
        try {
            Summoner summoner = summonerApiPort
                    .fetchSummonerByPuuid(puuid, regionType, requestExecutor)
                    .join();

            log.info("getSummonerByPuuid region type {} and puuid {}", regionType, puuid);
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
}
