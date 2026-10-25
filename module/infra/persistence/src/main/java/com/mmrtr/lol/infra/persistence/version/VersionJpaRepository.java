package com.mmrtr.lol.infra.persistence.version;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VersionJpaRepository extends JpaRepository<VersionEntity, Long> {

    /** 최신 버전부터 {@code pageable} 크기만큼. */
    @Query("SELECT v FROM VersionEntity v ORDER BY v.versionId DESC")
    List<VersionEntity> findRecentVersions(Pageable pageable);

    List<VersionEntity> findAllBySeasonIdInOrderByVersionIdDesc(Collection<Long> seasonIds);
}
