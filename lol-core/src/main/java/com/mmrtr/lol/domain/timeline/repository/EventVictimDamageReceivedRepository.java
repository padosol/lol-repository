package com.mmrtr.lol.domain.timeline.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventVictimDamageReceivedRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public EventVictimDamageReceivedRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(EventVictimDamageReceivedRepository eventVictimDamageReceivedRepository) {

    }
}
