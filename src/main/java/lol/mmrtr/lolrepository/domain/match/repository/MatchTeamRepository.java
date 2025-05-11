package lol.mmrtr.lolrepository.domain.match.repository;


import lol.mmrtr.lolrepository.domain.match.entity.MatchTeam;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MatchTeamRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;


    public MatchTeamRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    public void bulkSave(List<MatchTeam> matchTeams) {

        String sql = " INSERT INTO match_team (" +
                "team_id," +
                "match_id," +
                "win," +
                "baron_first," +
                "baron_kills," +
                "champion_first," +
                "champion_kills," +
                "dragon_first," +
                "dragon_kills," +
                "inhibitor_first," +
                "inhibitor_kills," +
                "rift_herald_first," +
                "rift_herald_kills," +
                "tower_first," +
                "tower_kills," +
                "champion1id," +
                "pick1turn," +
                "champion2id," +
                "pick2turn," +
                "champion3id," +
                "pick3turn," +
                "champion4id," +
                "pick4turn," +
                "champion5id," +
                "pick5turn "+
                ") VALUES (" +
                ":teamId," +
                ":matchId," +
                ":win," +
                ":baronFirst," +
                ":baronKills," +
                ":championFirst," +
                ":championKills," +
                ":dragonFirst," +
                ":dragonKills," +
                ":inhibitorFirst," +
                ":inhibitorKills," +
                ":riftHeraldFirst," +
                ":riftHeraldKills," +
                ":towerFirst," +
                ":towerKills," +
                ":champion1Id," +
                ":pick1Turn," +
                ":champion2Id," +
                ":pick2Turn," +
                ":champion3Id," +
                ":pick3Turn," +
                ":champion4Id," +
                ":pick4Turn," +
                ":champion5Id," +
                ":pick5Turn " +
                ") ON CONFLICT (team_id, match_id) DO NOTHING";

        SqlParameterSource[] params = SqlParameterSourceUtils.createBatch(matchTeams);
        jdbcTemplate.batchUpdate(sql, params);
    }
}
