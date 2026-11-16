package com.mmrtr.lol.domain.league.application.port;

import com.mmrtr.lol.common.type.Tier;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermits;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Riot 리그 조회. 호출하는 쪽이 받은 허가를 넘긴다 (리그 항목은 {@code {LEAGUE_ENTRIES: 1}}). */
public interface LeagueApiPort {

    /** 챌린저 1 + 그랜드마스터 1. */
    Map<RiotMethod, Integer> APEX_COST = Map.of(
            RiotMethod.CHALLENGER_LEAGUE, 1,
            RiotMethod.GRANDMASTER_LEAGUE, 1);

    Map<Tier, List<LeagueEntry>> getApexEntries(String queue, String platformName, RiotPermits permits);

    Set<LeagueEntry> getLeagueEntries(String queue, String tier, String division, int page, String platformName,
                                      RiotPermits permits);

    record LeagueEntry(
            String puuid,
            int leaguePoints,
            String rank,
            int wins,
            int losses,
            boolean veteran,
            boolean inactive,
            boolean freshBlood,
            boolean hotStreak
    ) {}
}
