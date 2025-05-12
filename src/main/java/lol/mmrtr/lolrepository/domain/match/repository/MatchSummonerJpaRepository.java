package lol.mmrtr.lolrepository.domain.match.repository;

import lol.mmrtr.lolrepository.domain.match.entity.MatchSummoner;
import lol.mmrtr.lolrepository.domain.match.entity.id.MatchSummonerId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchSummonerJpaRepository extends JpaRepository<MatchSummoner, MatchSummonerId> {
}
