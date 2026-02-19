package com.mmrtr.lol.infra.persistence.champion_stat.service;

import com.mmrtr.lol.domain.champion_stat.domain.ItemMetadata;
import com.mmrtr.lol.domain.champion_stat.service.usecase.ItemMetadataUseCase;
import com.mmrtr.lol.infra.persistence.champion_stat.entity.ItemMetadataEntity;
import com.mmrtr.lol.infra.persistence.champion_stat.repository.ItemMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ItemMetadataService implements ItemMetadataUseCase {

    private final ItemMetadataJpaRepository itemMetadataJpaRepository;

    @Override
    public ItemMetadata save(ItemMetadata itemMetadata) {
        ItemMetadataEntity entity = ItemMetadataEntity.builder()
                .itemId(itemMetadata.getItemId())
                .itemName(itemMetadata.getItemName())
                .itemCategory(itemMetadata.getItemCategory())
                .gameVersion(itemMetadata.getGameVersion())
                .build();
        ItemMetadataEntity saved = itemMetadataJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public List<ItemMetadata> saveAll(List<ItemMetadata> metadataList) {
        List<ItemMetadataEntity> entities = metadataList.stream()
                .map(m -> ItemMetadataEntity.builder()
                        .itemId(m.getItemId())
                        .itemName(m.getItemName())
                        .itemCategory(m.getItemCategory())
                        .gameVersion(m.getGameVersion())
                        .build())
                .toList();
        return itemMetadataJpaRepository.saveAll(entities).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ItemMetadata> findAll() {
        return itemMetadataJpaRepository.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    private ItemMetadata toDomain(ItemMetadataEntity entity) {
        return ItemMetadata.builder()
                .itemId(entity.getItemId())
                .itemName(entity.getItemName())
                .itemCategory(entity.getItemCategory())
                .gameVersion(entity.getGameVersion())
                .build();
    }
}
