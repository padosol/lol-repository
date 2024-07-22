package lol.mmrtr.lolrepository.repository;

import lol.mmrtr.lolrepository.entity.LeagueSummoner;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class LeagueSummonerRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public LeagueSummonerRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    public void save(LeagueSummoner leagueSummoner) {

        String sql = " INSERT INTO league_summoner(" +
                " summoner_id, " +
                " league_id, " +
                " create_at, " +
                " league_points, " +
                " rank, " +
                " wins, " +
                " losses, " +
                " veteran, " +
                " inactive, " +
                " fresh_blood, " +
                " hot_streak ) " +
                "VALUES(" +
                ":summonerId," +
                ":leagueId," +
                ":createAt," +
                ":leaguePoints," +
                ":rank," +
                ":wins," +
                ":losses," +
                ":veteran," +
                ":inactive," +
                ":freshBlood," +
                ":hotStreak" +
                ")";

        SqlParameterSource param = new BeanPropertySqlParameterSource(leagueSummoner);
        jdbcTemplate.update(sql, param);
    }


}
