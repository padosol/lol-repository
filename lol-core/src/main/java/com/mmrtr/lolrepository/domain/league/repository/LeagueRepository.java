package com.mmrtr.lolrepository.domain.league.repository;

import com.mmrtr.lolrepository.domain.league.entity.League;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

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