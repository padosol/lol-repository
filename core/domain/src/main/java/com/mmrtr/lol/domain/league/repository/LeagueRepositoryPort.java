package com.mmrtr.lol.domain.league.repository;

import com.mmrtr.lol.domain.league.domain.League;

import java.util.Optional;

public interface LeagueRepositoryPort {

    League save(League league);

    Optional<League> findById(String leagueId);
}
