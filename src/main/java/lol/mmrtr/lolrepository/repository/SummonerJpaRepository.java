package lol.mmrtr.lolrepository.repository;

import lol.mmrtr.lolrepository.entity.Summoner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SummonerJpaRepository extends JpaRepository<Summoner, String> {
}
