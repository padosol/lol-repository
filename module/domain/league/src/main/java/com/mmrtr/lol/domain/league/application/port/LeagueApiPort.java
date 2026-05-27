package com.mmrtr.lol.domain.league.application.port;

import com.mmrtr.lol.common.type.Tier;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface LeagueApiPort {

    Map<Tier, List<LeagueEntry>> getApexEntries(String queue, String platformName);

    Set<LeagueEntry> getLeagueEntries(String queue, String tier, String division, int page, String platformName);

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
