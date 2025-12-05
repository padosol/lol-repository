package com.mmrtr.lolrepository.domain.match.repository;

import com.mmrtr.lolrepository.domain.match.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MatchJpaRepository extends JpaRepository<Match, String> {

    List<Match> findAllByMatchIdIsNotIn(Collection<String> matchIds);
}
