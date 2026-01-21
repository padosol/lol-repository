package com.mmrtr.lol.infra.persistence.match.repository;

import com.mmrtr.lol.infra.persistence.match.entity.MatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MatchJpaRepository extends JpaRepository<MatchEntity, String> {

    List<MatchEntity> findAllByMatchIdIsNotIn(Collection<String> matchIds);
}
