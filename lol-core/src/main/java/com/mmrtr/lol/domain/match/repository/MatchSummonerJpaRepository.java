package com.mmrtr.lol.domain.match.repository;

import com.mmrtr.lol.domain.match.entity.MatchSummonerEntity;
import com.mmrtr.lol.domain.match.entity.id.MatchSummonerId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchSummonerJpaRepository extends JpaRepository<MatchSummonerEntity, MatchSummonerId> {
}
