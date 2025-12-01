package lol.mmrtr.lolrepository.domain.summoner.repository;

import lol.mmrtr.lolrepository.domain.summoner.entity.Summoner;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SummonerRepository{

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SummonerJpaRepository summonerJpaRepository;

    public Summoner save(Summoner summoner) {
        return summonerJpaRepository.save(summoner);
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
