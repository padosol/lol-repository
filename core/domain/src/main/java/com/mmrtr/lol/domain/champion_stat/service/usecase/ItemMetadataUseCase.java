package com.mmrtr.lol.domain.champion_stat.service.usecase;

import com.mmrtr.lol.domain.champion_stat.domain.ItemMetadata;

import java.util.List;

public interface ItemMetadataUseCase {

    ItemMetadata save(ItemMetadata itemMetadata);

    List<ItemMetadata> saveAll(List<ItemMetadata> metadataList);

    List<ItemMetadata> findAll();
}
