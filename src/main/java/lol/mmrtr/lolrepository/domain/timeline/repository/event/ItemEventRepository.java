package lol.mmrtr.lolrepository.domain.timeline.repository.event;

import lol.mmrtr.lolrepository.domain.entity.event.ItemEvents;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ItemEventRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;


    public ItemEventRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    public void bulkSave(List<ItemEvents> itemEvents) {

        String sql = "INSERT INTO item_events(" +
                "match_id," +
                "timeline_timestamp," +
                "item_id," +
                "participant_id," +
                "timestamp," +
                "type," +
                "after_id," +
                "before_id," +
                "gold_gain "+
                ") VALUES (" +
                ":matchId," +
                ":timelineTimestamp," +
                ":itemId," +
                ":participantId," +
                ":timestamp," +
                ":type," +
                ":afterId," +
                ":beforeId," +
                ":goldGain "+
                ")";

        SqlParameterSource[] params = SqlParameterSourceUtils.createBatch(itemEvents);

        jdbcTemplate.batchUpdate(sql, params);
    }

}
