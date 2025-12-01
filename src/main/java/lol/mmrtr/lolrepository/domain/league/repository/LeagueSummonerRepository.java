package lol.mmrtr.lolrepository.domain.league.repository;

import lol.mmrtr.lolrepository.domain.league.entity.LeagueSummoner;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LeagueSummonerRepository {

    private final LeagueSummonerJpaRepository leagueSummonerJpaRepository;


    public LeagueSummoner save(LeagueSummoner leagueSummoner) {
        return leagueSummonerJpaRepository.save(leagueSummoner);
    }

    public LeagueSummoner findBy(String puuid, String leagueId) {
        return leagueSummonerJpaRepository.findByPuuidAndLeagueId(puuid, leagueId).orElse(null);
    }

}
