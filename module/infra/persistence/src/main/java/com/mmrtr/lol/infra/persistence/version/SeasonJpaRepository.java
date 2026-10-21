package com.mmrtr.lol.infra.persistence.version;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonJpaRepository extends JpaRepository<SeasonEntity, Long> {

    List<SeasonEntity> findAllByOrderBySeasonValueDesc();
}
