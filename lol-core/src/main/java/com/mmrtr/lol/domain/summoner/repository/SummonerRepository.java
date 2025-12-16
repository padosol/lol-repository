package com.mmrtr.lol.domain.summoner.repository;

import com.mmrtr.lol.domain.summoner.entity.SummonerEntity;
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

    public SummonerEntity save(SummonerEntity summonerEntity) {
        return summonerJpaRepository.save(summonerEntity);
    }

    private void insert(SummonerEntity summonerEntity) {
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

        SqlParameterSource param = new BeanPropertySqlParameterSource(summonerEntity);

        jdbcTemplate.update(sql, param);
    }


}
