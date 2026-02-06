package com.mmrtr.lol.infra.persistence.league.repository;

import com.mmrtr.lol.domain.league.domain.LeagueSummoner;
import com.mmrtr.lol.domain.league.repository.LeagueSummonerRepositoryPort;
import com.mmrtr.lol.infra.persistence.league.entity.LeagueSummonerEntity;
import com.mmrtr.lol.infra.persistence.league.entity.LeagueSummonerHistoryEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LeagueSummonerRepositoryImpl implements LeagueSummonerRepositoryPort {

    private final LeagueSummonerJpaRepository jpaRepository;
    private final LeagueSummonerHistoryJpaRepository historyJpaRepository;

    @Override
    public LeagueSummoner save(LeagueSummoner leagueSummoner) {
        Optional<LeagueSummonerEntity> existingEntity =
                jpaRepository.findAllByPuuidAndQueue(leagueSummoner.getPuuid(), leagueSummoner.getQueue());

        if (existingEntity.isPresent()) {
            LeagueSummonerEntity entity = existingEntity.get();

            LeagueSummonerHistoryEntity history =
                    LeagueSummonerHistoryEntity.fromLeagueSummonerEntity(entity);
            historyJpaRepository.save(history);

            entity.update(leagueSummoner);
            return entity.toDomain();
        } else {
            LeagueSummonerEntity entity = LeagueSummonerEntity.fromDomain(leagueSummoner);
            return jpaRepository.save(entity).toDomain();
        }
    }

    @Override
    public Optional<LeagueSummoner> findBy(String puuid, String leagueId) {
        return jpaRepository.findByPuuidAndLeagueId(puuid, leagueId)
                .map(LeagueSummonerEntity::toDomain);
    }

    @Override
    public Optional<LeagueSummoner> findByPuuidAndQueue(String puuid, String queue) {
        return jpaRepository.findAllByPuuidAndQueue(puuid, queue)
                .map(LeagueSummonerEntity::toDomain);
    }
}
