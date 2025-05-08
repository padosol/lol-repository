package lol.mmrtr.lolrepository.domain.league_summoner.repository;

import lol.mmrtr.lolrepository.domain.league_summoner.entity.LeagueSummoner;
import lol.mmrtr.lolrepository.domain.league_summoner.entity.LeagueSummonerId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeagueSummonerJpaRepository extends JpaRepository<LeagueSummoner, LeagueSummonerId>{
}
