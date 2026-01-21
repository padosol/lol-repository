package com.mmrtr.lol.infra.persistence.match.repository;

import com.mmrtr.lol.infra.persistence.match.entity.MatchEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MatchRepositoryImpl {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MatchJpaRepository matchJpaRepository;

    public List<String> findAllMatchIdByIdsNotIn(Collection<String> matchIds) {
        List<MatchEntity> allByMatchIdIsNotIn = matchJpaRepository.findAllByMatchIdIsNotIn(matchIds);

        if (allByMatchIdIsNotIn.isEmpty()) {
            return new ArrayList<>();
        }

        return matchJpaRepository.findAllByMatchIdIsNotIn(matchIds).stream().map(MatchEntity::getMatchId).toList();
    }

    public List<MatchEntity> findAllByIds(Collection<String> matchIds) {
        return matchJpaRepository.findAllById(matchIds);
    }

    public MatchEntity save(MatchEntity match){
        return matchJpaRepository.save(match);
    }

    public List<MatchEntity> saveAll(List<MatchEntity> matches) {
        return matchJpaRepository.saveAll(matches);
    }

    public void bulkSave(List<MatchEntity> matches) {
        String sql = insertSql();

        SqlParameterSource[] params = SqlParameterSourceUtils.createBatch(matches);

        jdbcTemplate.batchUpdate(sql, params);
    }

    public String insertSql() {
        return " INSERT INTO \"match\" (" +
                "match_id," +
                "date_version," +
                "end_of_game_result," +
                "game_creation," +
                "game_duration," +
                "game_end_timestamp," +
                "game_start_timestamp," +
                "game_id," +
                "game_mode," +
                "game_name," +
                "game_type," +
                "game_version," +
                "map_id," +
                "queue_id," +
                "platform_id," +
                "tournament_code," +
                "season," +
                "game_create_datetime," +
                "game_end_datetime," +
                "game_start_datetime" +
                ") VALUES (" +
                ":matchId," +
                ":dateVersion," +
                ":endOfGameResult," +
                ":gameCreation," +
                ":gameDuration," +
                ":gameEndTimestamp," +
                ":gameStartTimestamp," +
                ":gameId," +
                ":gameMode," +
                ":gameName," +
                ":gameType," +
                ":gameVersion," +
                ":mapId," +
                ":queueId," +
                ":platformId," +
                ":tournamentCode," +
                ":season," +
                ":gameCreateDatetime," +
                ":gameEndDatetime," +
                ":gameStartDatetime "+
                ") ON CONFLICT (match_id) DO NOTHING";
    }
}
