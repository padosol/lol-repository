package com.mmrtr.lol.domain.match.repository;

import com.mmrtr.lol.domain.match.entity.ChallengesEntity;
import com.mmrtr.lol.domain.match.entity.id.MatchSummonerId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChallengesJpaRepository extends JpaRepository<ChallengesEntity, MatchSummonerId> {
}
