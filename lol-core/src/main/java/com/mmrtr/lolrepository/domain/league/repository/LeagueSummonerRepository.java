package com.mmrtr.lolrepository.domain.league.repository;

import com.mmrtr.lolrepository.domain.league.entity.LeagueSummoner;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LeagueSummonerRepository {

    private final LeagueSummonerJpaRepository leagueSummonerJpaRepository;


    public LeagueSummoner save(LeagueSummoner leagueSummoner) {
        return leagueSummonerJpaRepository.save(leagueSummoner);
    }

    public LeagueSummoner findBy(String puuid, String leagueId) {
        return leagueSummonerJpaRepository.findByPuuidAndLeagueId(puuid, leagueId).orElse(null);
    }

    public LeagueSummoner findAllByPuuid(String puuid, String queue) {
        return leagueSummonerJpaRepository.findAllByPuuidAndQueue(puuid, queue).orElse(null);
    }

    public Optional<LeagueSummoner> findAllByPuuidAndQueueOptional(String puuid, String queue) {
        return leagueSummonerJpaRepository.findAllByPuuidAndQueue(puuid, queue);
    }


}
