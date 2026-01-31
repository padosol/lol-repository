package com.mmrtr.lol.infra.persistence.league.repository;

import com.mmrtr.lol.infra.persistence.league.entity.TierCutoffEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TierCutoffJpaRepository extends JpaRepository<TierCutoffEntity, Long> {

    List<TierCutoffEntity> findByQueue(String queue);

    @Modifying
    @Query("DELETE FROM TierCutoffEntity tc WHERE tc.queue = :queue")
    void deleteByQueue(@Param("queue") String queue);
}
