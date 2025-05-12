package lol.mmrtr.lolrepository.domain.timeline.repository;

import lol.mmrtr.lolrepository.domain.entity.TimeLineEvent;
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

        String sql = " INSERT INTO time_line_event ( match_id, timestamp ) VALUES (:matchId, :timestamp) ON CONFLICT (match_id, timestamp) DO NOTHING ";

        SqlParameterSource[] param = SqlParameterSourceUtils.createBatch(timeLineEvents);
        
        jdbcTemplate.batchUpdate(sql, param);
    }


}
