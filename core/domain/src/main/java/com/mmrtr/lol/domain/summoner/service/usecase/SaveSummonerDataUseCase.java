package com.mmrtr.lol.domain.summoner.service.usecase;

import com.mmrtr.lol.domain.league.domain.League;
import com.mmrtr.lol.domain.league.domain.LeagueSummoner;
import com.mmrtr.lol.domain.league.repository.LeagueRepositoryPort;
import com.mmrtr.lol.domain.league.repository.LeagueSummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;
import com.mmrtr.lol.domain.summoner.repository.SummonerRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaveSummonerDataUseCase {

    private final SummonerRepositoryPort summonerRepositoryPort;
    private final LeagueRepositoryPort leagueRepositoryPort;
    private final LeagueSummonerRepositoryPort leagueSummonerRepositoryPort;

    @Transactional
    public void execute(Summoner summoner) {
        summonerRepositoryPort.save(summoner);

        for (LeagueInfo leagueInfo : summoner.getLeagueInfos()) {
            leagueRepositoryPort.saveIfAbsent(League.builder()
                    .leagueId(leagueInfo.getLeagueId())
                    .queue(leagueInfo.getQueueType())
                    .tier(leagueInfo.getTier())
                    .build());

            LeagueSummoner leagueSummoner = LeagueSummoner.builder()
                    .puuid(summoner.getPuuid())
                    .leagueId(leagueInfo.getLeagueId())
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
