package com.mmrtr.lol.infra.persistence.match.repository;

import com.mmrtr.lol.infra.persistence.match.entity.timeline.TimelineEventFrameEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class TimelineEventJsonRepositoryImpl {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void bulkSaveTimelineEventFrames(List<TimelineEventFrameEntity> entities) {
        if (entities.isEmpty()) return;

        String sql = "INSERT INTO timeline_event_frame (" +
                "match_id, timestamp, event_index, data" +
                ") VALUES (" +
                ":matchId, :timestamp, :eventIndex, CAST(:data AS jsonb)" +
                ") ON CONFLICT (match_id, timestamp, event_index) DO NOTHING";

        SqlParameterSource[] params = entities.stream()
                .map(e -> new MapSqlParameterSource()
                        .addValue("matchId", e.getMatchId())
                        .addValue("timestamp", e.getTimestamp())
                        .addValue("eventIndex", e.getEventIndex())
                        .addValue("data", e.getData()))
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    @Transactional(readOnly = true)
    public Set<String> findExistingMatchIds(List<String> matchIds) {
        if (matchIds.isEmpty()) return Collections.emptySet();

        String sql = "SELECT DISTINCT match_id FROM timeline_event_frame WHERE match_id IN (:matchIds)";
        MapSqlParameterSource params = new MapSqlParameterSource("matchIds", matchIds);
        List<String> result = jdbcTemplate.queryForList(sql, params, String.class);
        return new HashSet<>(result);
    }
}
