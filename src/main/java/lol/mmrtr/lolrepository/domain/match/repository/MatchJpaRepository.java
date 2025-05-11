package lol.mmrtr.lolrepository.domain.match.repository;

import lol.mmrtr.lolrepository.domain.match.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchJpaRepository extends JpaRepository<Match, String> {
}
