package com.mmrtr.lol.infra.persistence.league.repository;

import com.mmrtr.lol.domain.league.application.port.RankingQueryPort;
import com.mmrtr.lol.domain.league.domain.SummonerRanking;
import com.mmrtr.lol.domain.league.domain.TierCutoff;
import com.mmrtr.lol.infra.persistence.league.entity.SummonerRankingEntity;
import com.mmrtr.lol.infra.persistence.league.entity.TierCutoffEntity;
import com.mmrtr.lol.support.page.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 공개 API 랭킹·티어 컷오프 조회 어댑터. 조회 조건은 lol-server {@code RankPersistenceAdapter}·{@code TierCutoffPersistenceAdapter} 와 같다. */
@Component
@RequiredArgsConstructor
public class RankingQueryPersistenceAdapter implements RankingQueryPort {

    private final SummonerRankingJpaRepository summonerRankingJpaRepository;
    private final TierCutoffJpaRepository tierCutoffJpaRepository;

    /** 본문 SELECT 와 COUNT 를 한 트랜잭션(한 커넥션)으로 묶는다. 파생 쿼리 메서드에는 기본 트랜잭션이 없다. */
    @Override
    @Transactional(readOnly = true)
    public PageResult<SummonerRanking> findRankings(String platformId, String queue, String tier, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("currentRank").ascending());
        Page<SummonerRankingEntity> entities = tier == null
                ? summonerRankingJpaRepository.findByPlatformIdAndQueue(platformId, queue, pageable)
                : summonerRankingJpaRepository.findByPlatformIdAndQueueAndTier(platformId, queue, tier, pageable);

        return new PageResult<>(
                entities.getContent().stream().map(SummonerRankingEntity::toDomain).toList(),
                entities.getNumber() + 1,
                entities.getSize(),
                entities.getTotalElements(),
                entities.getTotalPages(),
                entities.isFirst(),
                entities.isLast());
    }

    @Override
    public List<TierCutoff> findTierCutoffs(String platformId, String queue, String tier) {
        List<TierCutoffEntity> entities;
        if (queue == null) {
            entities = tierCutoffJpaRepository.findByPlatformIdOrderByIdAsc(platformId);
        } else if (tier == null) {
            entities = tierCutoffJpaRepository.findByPlatformIdAndQueueOrderByIdAsc(platformId, queue);
        } else {
            entities = tierCutoffJpaRepository.findByPlatformIdAndQueueAndTierOrderByIdAsc(platformId, queue, tier);
        }
        return entities.stream().map(TierCutoffEntity::toDomain).toList();
    }
}
