package com.mmrtr.lol.domain.summoner.service;

import com.mmrtr.lol.domain.league.domain.League;
import com.mmrtr.lol.domain.league.domain.LeagueSummoner;
import com.mmrtr.lol.domain.league.repository.LeagueRepositoryPort;
import com.mmrtr.lol.domain.league.repository.LeagueSummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;
import com.mmrtr.lol.domain.summoner.repository.SummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.service.port.SummonerApiPort;
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
    private final SummonerRepositoryPort summonerRepositoryPort;
    private final LeagueRepositoryPort leagueRepositoryPort;
    private final LeagueSummonerRepositoryPort leagueSummonerRepositoryPort;
    private final Executor requestExecutor;

    @Transactional
    public Summoner getSummonerInfoV2(String regionType, String gameName, String tagLine) {
        try {
            Summoner summoner = summonerApiPort
                    .fetchSummonerByRiotId(gameName, tagLine, regionType, requestExecutor)
                    .join();

            log.info("getSummonerInfoV2 region type {} and gameName {}", regionType, gameName);
            saveSummonerData(summoner);

            return summoner;

        } catch (RuntimeException e) {
            log.error("Error fetching summoner info: {}", e.getMessage());
            throw new CoreException(ErrorType.NOT_FOUND_USER, "유저 정보 조회 중 오류가 발생했습니다.");
        }
    }

    private void saveSummonerData(Summoner summoner) {
        summonerRepositoryPort.save(summoner);

        for (LeagueInfo leagueInfo : summoner.getLeagueInfos()) {
            String leagueId = leagueInfo.getLeagueId();
            League league = leagueRepositoryPort.findById(leagueId).orElse(null);

            if (league == null) {
                league = leagueRepositoryPort.save(League.builder()
                        .leagueId(leagueInfo.getLeagueId())
                        .queue(leagueInfo.getQueueType())
                        .tier(leagueInfo.getTier())
                        .build());
            }

            LeagueSummoner savedLeagueSummoner = leagueSummonerRepositoryPort
                    .findBy(summoner.getPuuid(), league.getLeagueId())
                    .orElse(null);

            if (savedLeagueSummoner == null) {
                LeagueSummoner leagueSummoner = LeagueSummoner.builder()
                        .puuid(summoner.getPuuid())
                        .leagueId(league.getLeagueId())
                        .queue(leagueInfo.getQueueType())
                        .tier(leagueInfo.getTier())
                        .rank(leagueInfo.getRank())
                        .leaguePoints(leagueInfo.getLeaguePoints())
                        .wins(leagueInfo.getWins())
                        .losses(leagueInfo.getLosses())
                        .hotStreak(leagueInfo.isHotStreak())
                        .veteran(leagueInfo.isVeteran())
                        .freshBlood(leagueInfo.isFreshBlood())
                        .inactive(leagueInfo.isInactive())
                        .build();
                leagueSummonerRepositoryPort.save(leagueSummoner);
            }
        }
    }
}
