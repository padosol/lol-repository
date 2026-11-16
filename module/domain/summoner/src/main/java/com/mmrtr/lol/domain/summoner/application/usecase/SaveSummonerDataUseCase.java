package com.mmrtr.lol.domain.summoner.application.usecase;

import com.mmrtr.lol.domain.league.domain.League;
import com.mmrtr.lol.domain.league.domain.LeagueSummoner;
import com.mmrtr.lol.domain.league.application.port.LeagueRepositoryPort;
import com.mmrtr.lol.domain.league.application.port.LeagueSummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;
import com.mmrtr.lol.domain.summoner.application.port.SummonerRepositoryPort;
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
            // Riot league-v4 puuid 전환(2025)으로 leagueId 미제공 — League 저장만
            // 건너뛴다. tier/rank/LP 는 아래 LeagueSummoner 가 보존한다.
            if (leagueInfo.getLeagueId() != null) {
                leagueRepositoryPort.saveIfAbsent(League.builder()
                        .leagueId(leagueInfo.getLeagueId())
                        .queue(leagueInfo.getQueueType())
                        .tier(leagueInfo.getTier())
                        .build());
            }

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
