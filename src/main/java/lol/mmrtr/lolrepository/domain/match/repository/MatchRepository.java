package lol.mmrtr.lolrepository.domain.match.repository;

import lol.mmrtr.lolrepository.domain.match.entity.Match;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MatchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MatchJpaRepository matchJpaRepository;

    public List<String> findAllMatchIdByIdsNotIn(Collection<String> matchIds) {
        List<Match> allByMatchIdIsNotIn = matchJpaRepository.findAllByMatchIdIsNotIn(matchIds);

        if (allByMatchIdIsNotIn.isEmpty()) {
            return new ArrayList<>();
        }

        return matchJpaRepository.findAllByMatchIdIsNotIn(matchIds).stream().map(Match::getMatchId).toList();
    }

    public List<Match> findAllByIds(Collection<String> matchIds) {
        return matchJpaRepository.findAllById(matchIds);
    }

    public Match save(Match match){
        return matchJpaRepository.save(match);
    }

    public List<Match> saveAll(List<Match> matches) {
        return matchJpaRepository.saveAll(matches);
    }

    public void bulkSave(List<Match> matches) {
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
