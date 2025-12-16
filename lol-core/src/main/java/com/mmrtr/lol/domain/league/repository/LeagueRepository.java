package com.mmrtr.lol.domain.league.repository;

import com.mmrtr.lol.domain.league.entity.League;
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

    public League save(League league ){
        return leagueJpaRepository.save(league);
    }

    public League findById(String leagueId) {
        return leagueJpaRepository.findById(leagueId).orElse(null);
    }

}