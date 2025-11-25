package lol.mmrtr.lolrepository.domain.league.repository;

import lol.mmrtr.lolrepository.domain.league.entity.LeagueSummonerDetail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeagueSummonerDetailJpaRepository extends JpaRepository<LeagueSummonerDetail, Long> {
}
