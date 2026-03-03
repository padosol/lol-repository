package com.mmrtr.lol.infra.persistence.match.repository;

import com.mmrtr.lol.infra.persistence.match.entity.MatchBanEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MatchBanRepositoryImpl {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MatchBanJpaRepository matchBanJpaRepository;

    public List<MatchBanEntity> saveAll(List<MatchBanEntity> matchBans) {
        return matchBanJpaRepository.saveAll(matchBans);
    }

    public void bulkSave(List<MatchBanEntity> matchBans) {
        String sql = " INSERT INTO match_ban (" +
                "match_id," +
                "team_id," +
                "champion_id," +
                "pick_turn" +
                ") VALUES (" +
                ":matchId," +
                ":teamId," +
                ":championId," +
                ":pickTurn" +
                ") ON CONFLICT (match_id, team_id, pick_turn) DO NOTHING";

        SqlParameterSource[] params = matchBans.stream()
                .map(param -> new MapSqlParameterSource()
                        .addValue("matchId", param.getMatchId())
                        .addValue("teamId", param.getTeamId())
                        .addValue("championId", param.getChampionId())
                        .addValue("pickTurn", param.getPickTurn())
                )
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }
}
