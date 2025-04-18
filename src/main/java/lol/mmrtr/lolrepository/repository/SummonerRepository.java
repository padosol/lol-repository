package lol.mmrtr.lolrepository.repository;

import lol.mmrtr.lolrepository.entity.Summoner;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class SummonerRepository{

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SummonerRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(Summoner summoner) {
        insert(summoner);
    }

    private void update(Summoner summoner) {
        String sql = " SELECT * FROM summoner where puuid = ?";
    }

    private void insert(Summoner summoner) {
        String sql = " INSERT INTO summoner (" +
                "profile_icon_id," +
                "revision_click_date," +
                "revision_date," +
                "summoner_level," +
                "account_id," +
                "game_name," +
                "puuid," +
                "region," +
                "summoner_id," +
                "tag_line) " +
                "VALUES(" +
                ":profileIconId," +
                ":revisionClickDate," +
                ":revisionDate," +
                ":summonerLevel," +
                ":accountId," +
                ":gameName," +
                ":puuid," +
                ":region," +
                ":summonerId," +
                ":tagLine"+
                ")";

        SqlParameterSource param = new BeanPropertySqlParameterSource(summoner);

        jdbcTemplate.update(sql, param);
    }


}
