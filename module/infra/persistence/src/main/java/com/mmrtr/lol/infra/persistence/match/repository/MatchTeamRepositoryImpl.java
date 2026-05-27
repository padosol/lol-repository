package com.mmrtr.lol.infra.persistence.match.repository;


import com.mmrtr.lol.infra.persistence.match.entity.MatchTeamEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MatchTeamRepositoryImpl {

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
                "atakhan_first," +
                "atakhan_kills," +
                "baron_first," +
                "baron_kills," +
                "champion_first," +
                "champion_kills," +
                "dragon_first," +
                "dragon_kills," +
                "horde_first," +
                "horde_kills," +
                "inhibitor_first," +
                "inhibitor_kills," +
                "rift_herald_first," +
                "rift_herald_kills," +
                "tower_first," +
                "tower_kills," +
                "feat_epic_monster_kill," +
                "feat_first_blood," +
                "feat_first_turret" +
                ") VALUES (" +
                ":teamId," +
                ":matchId," +
                ":win," +
                ":atakhanFirst," +
                ":atakhanKills," +
                ":baronFirst," +
                ":baronKills," +
                ":championFirst," +
                ":championKills," +
                ":dragonFirst," +
                ":dragonKills," +
                ":hordeFirst," +
                ":hordeKills," +
                ":inhibitorFirst," +
                ":inhibitorKills," +
                ":riftHeraldFirst," +
                ":riftHeraldKills," +
                ":towerFirst," +
                ":towerKills," +
                ":featEpicMonsterKill," +
                ":featFirstBlood," +
                ":featFirstTurret" +
                ") ON CONFLICT (team_id, match_id) DO NOTHING";

        SqlParameterSource[] params = matchTeams.stream()
                .map(param -> {
                    return new MapSqlParameterSource()
                            .addValue("teamId", param.getTeamId())
                            .addValue("matchId", param.getMatchId())
                            .addValue("win", param.isWin())
                            .addValue("atakhanFirst", param.isAtakhanFirst())
                            .addValue("atakhanKills", param.getAtakhanKills())
                            .addValue("baronFirst", param.isBaronFirst())
                            .addValue("baronKills", param.getBaronKills())
                            .addValue("championFirst", param.isChampionFirst())
                            .addValue("championKills", param.getChampionKills())
                            .addValue("dragonFirst", param.isDragonFirst())
                            .addValue("dragonKills", param.getDragonKills())
                            .addValue("hordeFirst", param.isHordeFirst())
                            .addValue("hordeKills", param.getHordeKills())
                            .addValue("inhibitorFirst", param.isInhibitorFirst())
                            .addValue("inhibitorKills", param.getInhibitorKills())
                            .addValue("riftHeraldFirst", param.isRiftHeraldFirst())
                            .addValue("riftHeraldKills", param.getRiftHeraldKills())
                            .addValue("towerFirst", param.isTowerFirst())
                            .addValue("towerKills", param.getTowerKills())
                            .addValue("featEpicMonsterKill", param.getFeatEpicMonsterKill())
                            .addValue("featFirstBlood", param.getFeatFirstBlood())
                            .addValue("featFirstTurret", param.getFeatFirstTurret())
                            ;
                })
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }
}
