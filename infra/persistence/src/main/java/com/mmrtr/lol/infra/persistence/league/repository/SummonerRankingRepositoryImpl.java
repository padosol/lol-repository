package com.mmrtr.lol.infra.persistence.league.repository;

import com.mmrtr.lol.domain.league.domain.SummonerRanking;
import com.mmrtr.lol.domain.league.repository.SummonerRankingRepositoryPort;
import com.mmrtr.lol.infra.persistence.league.entity.SummonerRankingEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SummonerRankingRepositoryImpl implements SummonerRankingRepositoryPort {

    private final SummonerRankingJpaRepository jpaRepository;
    private final EntityManager entityManager;

    private static final int BATCH_SIZE = 1000;

    @Override
    public List<SummonerRanking> saveAll(List<SummonerRanking> rankings) {
        List<SummonerRankingEntity> entities = rankings.stream()
                .map(SummonerRankingEntity::fromDomain)
                .toList();
        return jpaRepository.saveAll(entities).stream()
                .map(SummonerRankingEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void bulkSaveAll(List<SummonerRanking> rankings) {
        List<SummonerRankingEntity> entities = rankings.stream()
                .map(SummonerRankingEntity::fromDomain)
                .toList();

        for (int i = 0; i < entities.size(); i++) {
            entityManager.persist(entities.get(i));
            if ((i + 1) % BATCH_SIZE == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }

        if (entities.size() % BATCH_SIZE != 0) {
            entityManager.flush();
            entityManager.clear();
        }
    }

    @Override
    @Transactional
    public void deleteByQueue(String queue) {
        jpaRepository.deleteByQueue(queue);
    }

    @Override
    @Transactional
    public void backupCurrentRanks(String queue) {
        jpaRepository.backupCurrentRanks(queue);
    }

    @Override
    @Transactional
    public void updateRankChangesFromBackup(String queue) {
        jpaRepository.updateRankChangesFromBackup(queue);
    }

    @Override
    @Transactional
    public void clearBackup(String queue) {
        jpaRepository.clearBackup(queue);
    }
}
