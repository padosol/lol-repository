package com.mmrtr.lol.infra.persistence.queue_type;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueueJpaRepository extends JpaRepository<QueueEntity, Long> {

    List<QueueEntity> findAllByIsTab(boolean isTab);
}
