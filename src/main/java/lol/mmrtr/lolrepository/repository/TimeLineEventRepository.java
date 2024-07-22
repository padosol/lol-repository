package lol.mmrtr.lolrepository.repository;

import lol.mmrtr.lolrepository.entity.TimeLineEvent;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TimeLineEventRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TimeLineEventRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    public void bulkSave(List<TimeLineEvent> timeLineEvents) {

        String sql = " INSERT INTO time_line_event (" +
                "match_id," +
                "timestamp," +
                "event_timestamp," +
                "real_timestamp," +
                "type," +
                "item_id," +
                "participant_id," +
                "puuid," +
                "level_up_type," +
                "skill_slot," +
                "creator_id," +
                "ward_type," +
                "level," +
                "assisting_participant_ids," +
                "bounty," +
                "kill_streak_length," +
                "killer_id," +
                "x," +
                "y," +
                "victim_id," +
                "kill_type," +
                "lane_type," +
                "team_id," +
                "multi_kill_length," +
                "killer_team_id," +
                "monster_type," +
                "monster_sub_type," +
                "building_type," +
                "tower_type," +
                "after_id," +
                "before_id," +
                "gold_gain," +
                "game_id," +
                "winning_team," +
                "transform_type," +
                "name," +
                "shutdown_bounty," +
                "actual_start_time" +
                ") VALUES (" +
                ":matchId," +
                ":timestamp," +
                ":eventTimestamp," +
                ":realTimestamp," +
                ":type," +
                ":itemId," +
                ":participantId," +
                ":puuid," +
                ":levelUpType," +
                ":skillSlot," +
                ":creatorId," +
                ":wardType," +
                ":level," +
                ":assistingParticipantIds," +
                ":bounty," +
                ":killStreakLength," +
                ":killerId," +
                ":x," +
                ":y," +
                ":victimId," +
                ":killType," +
                ":laneType," +
                ":teamId," +
                ":multiKillLength," +
                ":killerTeamId," +
                ":monsterType," +
                ":monsterSubType," +
                ":buildingType," +
                ":towerType," +
                ":afterId," +
                ":beforeId," +
                ":goldGain," +
                ":gameId," +
                ":winningTeam," +
                ":transformType," +
                ":name," +
                ":shutdownBounty," +
                ":actualStartTime "+
                ") ";

        SqlParameterSource[] param = SqlParameterSourceUtils.createBatch(timeLineEvents);
        
        jdbcTemplate.batchUpdate(sql, param);
    }


}
