package lol.mmrtr.lolrepository.repository;

import lol.mmrtr.lolrepository.domain.league_summoner.entity.LeagueSummoner;
import lol.mmrtr.lolrepository.domain.league_summoner.repository.LeagueSummonerJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LeagueSummonerRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final LeagueSummonerJpaRepository leagueSummonerJpaRepository;


    public LeagueSummoner save(LeagueSummoner leagueSummoner) {
        return leagueSummonerJpaRepository.save(leagueSummoner);
    }
}
