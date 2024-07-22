package lol.mmrtr.lolrepository.repository;

import lol.mmrtr.lolrepository.entity.EventVictimDamageDealt;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventVictimDamageDealtRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public EventVictimDamageDealtRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    public void save(EventVictimDamageDealt eventVictimDamageDealt) {

    }
}
