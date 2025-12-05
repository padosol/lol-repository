package com.mmrtr.lolrepository.domain.match.repository;

import com.mmrtr.lolrepository.domain.match.entity.Challenges;
import com.mmrtr.lolrepository.domain.match.entity.id.MatchSummonerId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChallengesJpaRepository extends JpaRepository<Challenges, MatchSummonerId> {
}
