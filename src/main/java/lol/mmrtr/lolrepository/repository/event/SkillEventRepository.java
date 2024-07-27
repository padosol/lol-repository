package lol.mmrtr.lolrepository.repository.event;

import lol.mmrtr.lolrepository.entity.event.SkillEvents;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SkillEventRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;


    public SkillEventRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    public void bulkSave(List<SkillEvents> skillEvents) {

        String sql = "INSERT INTO skill_events (" +
                "match_id," +
                "timeline_timestamp," +
                "skill_slot," +
                "participant_id," +
                "level_up_type," +
                "timestamp," +
                "type "+
                ") VALUES (" +
                ":matchId," +
                ":timelineTimestamp," +
                ":skillSlot," +
                ":participantId," +
                ":levelUpType," +
                ":timestamp," +
                ":type "+
                ")";

        SqlParameterSource[] params = SqlParameterSourceUtils.createBatch(skillEvents);
        jdbcTemplate.batchUpdate(sql, params);
    }
}
