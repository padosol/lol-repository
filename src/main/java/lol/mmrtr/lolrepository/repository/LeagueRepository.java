package lol.mmrtr.lolrepository.repository;

import lol.mmrtr.lolrepository.entity.League;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class LeagueRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public LeagueRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(League league ){
        String sql = " INSERT INTO league(league_id, tier, name, queue) VALUES(:leagueId, :tier, :name, :queue)";

        SqlParameterSource param = new BeanPropertySqlParameterSource(league);

        jdbcTemplate.update(sql, param);
    }
}
