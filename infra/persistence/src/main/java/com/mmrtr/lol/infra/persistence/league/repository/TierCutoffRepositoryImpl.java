package com.mmrtr.lol.infra.persistence.league.repository;

import com.mmrtr.lol.domain.league.domain.TierCutoff;
import com.mmrtr.lol.domain.league.repository.TierCutoffRepositoryPort;
import com.mmrtr.lol.infra.persistence.league.entity.TierCutoffEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class TierCutoffRepositoryImpl implements TierCutoffRepositoryPort {

    private final TierCutoffJpaRepository jpaRepository;

    @Override
    public void saveAll(List<TierCutoff> cutoffs) {
        if (cutoffs.isEmpty()) {
            return;
        }

        String queue = cutoffs.get(0).getQueue();
        jpaRepository.deleteByQueue(queue);

        List<TierCutoffEntity> entities = cutoffs.stream()
                .map(TierCutoffEntity::fromDomain)
                .toList();
        jpaRepository.saveAll(entities);
    }

    @Override
    public List<TierCutoff> findByQueue(String queue) {
        return jpaRepository.findByQueue(queue).stream()
                .map(TierCutoffEntity::toDomain)
                .toList();
    }
}
