package com.mmrtr.lol.infra.persistence.match.repository;

import com.mmrtr.lol.infra.persistence.match.entity.MatchSummonerEntity;
import com.mmrtr.lol.infra.persistence.match.entity.id.MatchSummonerId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchSummonerJpaRepository extends JpaRepository<MatchSummonerEntity, MatchSummonerId> {
}
