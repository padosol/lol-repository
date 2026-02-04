package com.mmrtr.lol.domain.league.repository;

import com.mmrtr.lol.domain.league.domain.League;

import java.util.Optional;

public interface LeagueRepositoryPort {

    League save(League league);

    Optional<League> findById(String leagueId);

    /**
     * League가 존재하지 않으면 저장하고, 존재하면 기존 값을 반환합니다.
     * 분산 락을 사용하여 동시성 문제를 방지합니다.
     *
     * @param league 저장할 League 도메인 객체
     * @return 저장된 또는 기존 League
     */
    League saveIfAbsent(League league);
}
