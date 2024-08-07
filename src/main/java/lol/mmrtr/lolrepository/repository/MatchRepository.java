package lol.mmrtr.lolrepository.repository;

import lol.mmrtr.lolrepository.entity.Match;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class MatchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MatchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(Match match){

        String sql = " INSERT INTO \"match\" (" +
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

        SqlParameterSource param = new BeanPropertySqlParameterSource(match);

        jdbcTemplate.update(sql, param);
    }

}
