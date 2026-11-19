package com.mmrtr.lol.domain.match.domain;

import com.mmrtr.lol.domain.summoner.domain.LeagueSummoner;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record MatchTierSnapshot(Map<String, ParticipantTier> byPuuid, Integer averageTier) {

    public record ParticipantTier(String tier, String rank, int absolutePoints) {
    }

    public static MatchTierSnapshot of(List<String> participantPuuids,
                                       Map<String, LeagueSummoner> leagueByPuuid) {
        Map<String, ParticipantTier> byPuuid = new HashMap<>();
        long sum = 0;
        int count = 0;
        for (String puuid : participantPuuids) {
            LeagueSummoner league = leagueByPuuid.get(puuid);
            if (league == null) {
                continue;
            }
            byPuuid.put(puuid,
                    new ParticipantTier(league.getTier(), league.getRank(), league.getAbsolutePoints()));
            sum += league.getAbsolutePoints();
            count++;
        }
        Integer average = count == 0 ? null : (int) ((double) sum / count);
        return new MatchTierSnapshot(byPuuid, average);
    }

    public static MatchTierSnapshot empty() {
        return new MatchTierSnapshot(Map.of(), null);
    }

    public Optional<ParticipantTier> tierOf(String puuid) {
        return Optional.ofNullable(byPuuid.get(puuid));
    }
}
