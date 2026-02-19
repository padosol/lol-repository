package com.mmrtr.lol.infra.persistence.champion_stat.repository;

import com.mmrtr.lol.infra.persistence.champion_stat.entity.ItemMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemMetadataJpaRepository extends JpaRepository<ItemMetadataEntity, Integer> {
}
