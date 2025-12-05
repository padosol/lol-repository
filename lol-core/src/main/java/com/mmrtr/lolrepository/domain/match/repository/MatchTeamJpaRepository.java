package com.mmrtr.lolrepository.domain.match.repository;

import com.mmrtr.lolrepository.domain.match.entity.MatchTeam;
import com.mmrtr.lolrepository.domain.match.entity.id.MatchTeamId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchTeamJpaRepository extends JpaRepository<MatchTeam, MatchTeamId> {
}
