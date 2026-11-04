package com.mmrtr.lol.infra.persistence.summoner.repository;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.application.port.SummonerRepositoryPort;
import com.mmrtr.lol.infra.persistence.summoner.entity.SummonerEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SummonerRepositoryImpl implements SummonerRepositoryPort {

    private final SummonerJpaRepository jpaRepository;

    @Override
    public void save(Summoner summoner) {
        jpaRepository.save(SummonerEntity.fromDomain(summoner));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Summoner> findByPuuid(String puuid) {
        return jpaRepository.findById(puuid)
                .map(SummonerEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Summoner> findAllByPuuidIn(Collection<String> puuids) {
        return jpaRepository.findAllByPuuidIn(puuids).stream()
                .map(SummonerEntity::toDomain)
                .collect(Collectors.toMap(Summoner::getPuuid, summoner -> summoner));
    }

    @Override
    @Transactional
    public void updateLastRiotCallDate(String puuid) {
        jpaRepository.findById(puuid)
                .ifPresent(SummonerEntity::updateLastRiotCallDate);
    }

    @Override
    @Transactional
    public boolean updateStatusInfo(String puuid, int profileIconId, long summonerLevel) {
        return jpaRepository.findById(puuid)
                .map(entity -> {
                    entity.updateStatusInfo(profileIconId, summonerLevel);
                    return true;
                })
                .orElse(false);
    }

    @Override
    @Transactional
    public void updateGameIdentity(String puuid, String gameName, String tagLine) {
        jpaRepository.findById(puuid)
                .ifPresent(entity -> entity.updateGameIdentity(gameName, tagLine));
    }

    @Override
    @Transactional
    public void updateRevisionDate(String puuid, LocalDateTime revisionDate) {
        jpaRepository.findById(puuid)
                .ifPresent(entity -> entity.updateRevisionDate(revisionDate));
    }
}
