package com.mmrtr.lol.domain.match.repository;


import com.mmrtr.lol.domain.match.entity.MatchTeamEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MatchTeamRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MatchTeamJpaRepository matchTeamJpaRepository;

    public List<MatchTeamEntity> saveAll(List<MatchTeamEntity> matchTeams) {
        return matchTeamJpaRepository.saveAll(matchTeams);
    }


    public void bulkSave(List<MatchTeamEntity> matchTeams) {

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

        SqlParameterSource[] params = matchTeams.stream()
                .map(param -> {
                    return new MapSqlParameterSource()
                            .addValue("teamId", param.getTeamId())
                            .addValue("matchId", param.getMatchId())
                            .addValue("win", param.isWin())
                            .addValue("baronFirst", param.getTeamObject().isBaronFirst())
                            .addValue("baronKills", param.getTeamObject().getBaronKills())
                            .addValue("championFirst", param.getTeamObject().isChampionFirst())
                            .addValue("championKills", param.getTeamObject().getChampionKills())
                            .addValue("dragonFirst", param.getTeamObject().isDragonFirst())
                            .addValue("dragonKills", param.getTeamObject().getDragonKills())
                            .addValue("inhibitorFirst", param.getTeamObject().isInhibitorFirst())
                            .addValue("inhibitorKills", param.getTeamObject().getInhibitorKills())
                            .addValue("riftHeraldFirst", param.getTeamObject().isRiftHeraldFirst())
                            .addValue("riftHeraldKills", param.getTeamObject().getRiftHeraldKills())
                            .addValue("towerFirst", param.getTeamObject().isTowerFirst())
                            .addValue("towerKills", param.getTeamObject().getTowerKills())
                            .addValue("champion1Id", param.getTeamBan().getChampion1Id())
                            .addValue("pick1Turn", param.getTeamBan().getPick1Turn())
                            .addValue("champion2Id", param.getTeamBan().getChampion2Id())
                            .addValue("pick2Turn", param.getTeamBan().getPick2Turn())
                            .addValue("champion3Id", param.getTeamBan().getChampion3Id())
                            .addValue("pick3Turn", param.getTeamBan().getPick3Turn())
                            .addValue("champion4Id", param.getTeamBan().getChampion4Id())
                            .addValue("pick4Turn", param.getTeamBan().getPick4Turn())
                            .addValue("champion5Id", param.getTeamBan().getChampion5Id())
                            .addValue("pick5Turn", param.getTeamBan().getPick5Turn())
                            ;
                })
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }
}
