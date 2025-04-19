package lol.mmrtr.lolrepository.domain.league.repository;

import lol.mmrtr.lolrepository.domain.league.entity.League;
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

    public void save(League league ){
        String sql = " INSERT INTO league(league_id, tier, name, queue) VALUES(:leagueId, :tier, :name, :queue)";

        SqlParameterSource param = new BeanPropertySqlParameterSource(league);

        jdbcTemplate.update(sql, param);
    }

    public League findById(String leagueId) {
        return leagueJpaRepository.findById(leagueId).orElse(null);
    }

}