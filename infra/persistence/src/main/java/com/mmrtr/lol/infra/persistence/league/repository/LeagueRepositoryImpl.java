package com.mmrtr.lol.infra.persistence.league.repository;

import com.mmrtr.lol.domain.league.domain.League;
import com.mmrtr.lol.domain.league.repository.LeagueRepositoryPort;
import com.mmrtr.lol.infra.persistence.league.entity.LeagueEntity;
import com.mmrtr.lol.infra.redis.service.RedisLockHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class LeagueRepositoryImpl implements LeagueRepositoryPort {

    private static final String LEAGUE_LOCK_PREFIX = "league:lock:";
    private static final Duration LOCK_DURATION = Duration.ofSeconds(5);

    private final LeagueJpaRepository jpaRepository;
    private final RedisLockHandler redisLockHandler;

    @Override
    public League save(League league) {
        LeagueEntity entity = LeagueEntity.fromDomain(league);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<League> findById(String leagueId) {
        return jpaRepository.findById(leagueId)
                .map(LeagueEntity::toDomain);
    }

    @Override
    public League saveIfAbsent(League league) {
        String leagueId = league.getLeagueId();
        String lockKey = LEAGUE_LOCK_PREFIX + leagueId;

        Optional<League> result = redisLockHandler.executeWithLock(
                lockKey,
                LOCK_DURATION,
                () -> findById(leagueId).orElseGet(() -> save(league))
        );

        // 락 획득 실패 시 기존 데이터 조회만 수행 (이미 다른 요청이 저장 중)
        return result.orElseGet(() -> findById(leagueId).orElse(league));
    }
}
