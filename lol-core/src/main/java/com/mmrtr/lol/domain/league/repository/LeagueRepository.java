package com.mmrtr.lol.domain.league.repository;

import com.mmrtr.lol.domain.league.entity.LeagueEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LeagueRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private final LeagueJpaRepository leagueJpaRepository;


    public LeagueRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            LeagueJpaRepository leagueJpaRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.leagueJpaRepository = leagueJpaRepository;
    }

    public LeagueEntity save(LeagueEntity league ){
        return leagueJpaRepository.save(league);
    }

    public LeagueEntity findById(String leagueId) {
        return leagueJpaRepository.findById(leagueId).orElse(null);
    }

}