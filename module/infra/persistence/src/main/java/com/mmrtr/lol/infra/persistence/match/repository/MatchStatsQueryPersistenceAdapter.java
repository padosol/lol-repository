package com.mmrtr.lol.infra.persistence.match.repository;

import com.mmrtr.lol.common.type.Queue;
import com.mmrtr.lol.domain.match.application.port.MatchStatsQueryPort;
import com.mmrtr.lol.domain.match.readmodel.DailyGameCountReadModel.DailyCount;
import com.mmrtr.lol.domain.match.readmodel.RankChampionsReadModel.RankChampion;
import com.mmrtr.lol.domain.match.readmodel.RankChampionsReadModel.RankChampionRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 소환사별 매치 통계 어댑터. 집계 조건은 lol-server {@code MatchSummonerRepositoryCustomImpl} 과 같다. */
@Component
@RequiredArgsConstructor
public class MatchStatsQueryPersistenceAdapter implements MatchStatsQueryPort {

    private static final String DAILY_GAME_COUNTS_SQL = """
            SELECT CAST(m.game_create_datetime AS DATE) AS game_date, COUNT(*) AS game_count
            FROM match_participant mp
            JOIN match m ON m.match_id = mp.match_id
            WHERE mp.puuid = :puuid
              AND m.season = :season
              AND m.queue_id IN (:queueIds)
              AND m.game_create_datetime >= :from
            GROUP BY game_date
            ORDER BY game_date
            """;

    /** challenges 행이 없는 게임은 세지 않는다 (내부 조인). */
    private static final String RANK_CHAMPIONS_SQL = """
            SELECT m.queue_id, mp.champion_id, mp.champion_name,
                   AVG(mp.assists) AS assists, AVG(mp.deaths) AS deaths, AVG(mp.kills) AS kills,
                   SUM(CASE WHEN mp.win THEN 1 ELSE 0 END) AS wins,
                   SUM(CASE WHEN mp.win THEN 0 ELSE 1 END) AS losses,
                   AVG(c.damage_per_minute) AS damage_per_minute,
                   AVG(c.kda) AS kda,
                   AVG(c.lane_minions_first10minutes) AS lane_minions_first10minutes,
                   AVG(c.damage_taken_on_team_percentage) AS damage_taken_on_team_percentage,
                   AVG(c.gold_per_minute) AS gold_per_minute,
                   COUNT(*) AS play_count
            FROM match_participant mp
            JOIN match m ON m.match_id = mp.match_id
            JOIN match_participant_challenges c ON c.puuid = mp.puuid AND c.match_id = mp.match_id
            WHERE mp.puuid = :puuid
              AND m.queue_id IN (:queueIds)
            """;

    private static final List<Integer> RANKED_QUEUE_IDS =
            List.of(Queue.RANKED_SOLO_5x5.getQueueId(), Queue.RANKED_FLEX_SR.getQueueId());

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public List<DailyCount> findDailyGameCounts(String puuid, int season, List<Integer> queueIds,
                                                LocalDateTime from) {
        MapSqlParameterSource params = new MapSqlParameterSource("puuid", puuid)
                .addValue("season", season)
                .addValue("queueIds", queueIds)
                .addValue("from", from);
        return jdbcTemplate.query(DAILY_GAME_COUNTS_SQL, params, (rs, rowNum) -> new DailyCount(
                rs.getObject("game_date", LocalDate.class), rs.getLong("game_count")));
    }

    @Override
    public List<RankChampionRecord> findRankChampions(String puuid, Integer season) {
        StringBuilder sql = new StringBuilder(RANK_CHAMPIONS_SQL);
        MapSqlParameterSource params = new MapSqlParameterSource("puuid", puuid)
                .addValue("queueIds", RANKED_QUEUE_IDS);
        if (season != null) {
            sql.append(" AND m.season = :season");
            params.addValue("season", season);
        }
        // 판 수가 같으면 챔피언 ID 순 (lol-server 는 판 수로만 정렬한다)
        sql.append(" GROUP BY m.queue_id, mp.champion_id, mp.champion_name ORDER BY play_count DESC, mp.champion_id");
        return jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new RankChampionRecord(
                rs.getInt("queue_id"),
                RankChampion.of(
                        rs.getDouble("assists"),
                        rs.getDouble("deaths"),
                        rs.getDouble("kills"),
                        rs.getInt("champion_id"),
                        rs.getString("champion_name"),
                        rs.getLong("wins"),
                        rs.getLong("losses"),
                        rs.getDouble("damage_per_minute"),
                        rs.getDouble("kda"),
                        rs.getDouble("lane_minions_first10minutes"),
                        rs.getDouble("damage_taken_on_team_percentage"),
                        rs.getDouble("gold_per_minute"),
                        rs.getLong("play_count"))));
    }
}
