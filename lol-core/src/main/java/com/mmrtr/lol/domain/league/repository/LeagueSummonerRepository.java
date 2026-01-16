package com.mmrtr.lol.domain.league.repository;

import com.mmrtr.lol.domain.league.entity.LeagueSummonerEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LeagueSummonerRepository {

    private final LeagueSummonerJpaRepository leagueSummonerJpaRepository;


    public LeagueSummonerEntity save(LeagueSummonerEntity leagueSummoner) {
        return leagueSummonerJpaRepository.save(leagueSummoner);
    }

    public LeagueSummonerEntity findBy(String puuid, String leagueId) {
        return leagueSummonerJpaRepository.findByPuuidAndLeagueId(puuid, leagueId).orElse(null);
    }

    public LeagueSummonerEntity findAllByPuuid(String puuid, String queue) {
        return leagueSummonerJpaRepository.findAllByPuuidAndQueue(puuid, queue).orElse(null);
    }

    public Optional<LeagueSummonerEntity> findAllByPuuidAndQueueOptional(String puuid, String queue) {
        return leagueSummonerJpaRepository.findAllByPuuidAndQueue(puuid, queue);
    }


}
