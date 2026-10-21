package com.mmrtr.lol.domain.summoner.readmodel;

import com.mmrtr.lol.common.type.Queue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 소환사의 현재 랭크와 랭크 변동 이력. 필드·분류·{@code oow} 형식이 lol-server {@code LeagueResponse}
 * ({@code LeagueMapper}) 와 같다. 솔로 랭크가 아닌 큐는 모두 자유 랭크로 분류한다.
 */
public record SummonerLeagueReadModel(
        LeagueEntry soloLeague,
        LeagueEntry flexLeague,
        List<LeagueEntry> soloLeagueHistory,
        List<LeagueEntry> flexLeagueHistory
) {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal(100);
    private static final String SOLO_QUEUE = Queue.RANKED_SOLO_5x5.name();

    /**
     * @param oow 승률 문자열. 현재 랭크는 백분율({@code "50.00"}), 이력은 비율({@code "0.50"}) — lol-server 와 같다
     */
    public record LeagueEntry(String leagueType, int leaguePoints, int wins, int losses, String oow, String tier,
                              String rank) {
    }

    /** 저장된 랭크 상태 한 건 — {@code league_summoner}(현재) 또는 {@code league_summoner_history}(이력)의 행. */
    public record LeagueRecord(String queue, int leaguePoints, int wins, int losses, String tier, String rank) {
    }

    /**
     * @param currents  현재 랭크 (같은 분류가 여럿이면 뒤의 것)
     * @param histories 이력, 응답에 그대로의 순서로 담긴다 (최신순으로 넘긴다)
     */
    public static SummonerLeagueReadModel of(List<LeagueRecord> currents, List<LeagueRecord> histories) {
        LeagueEntry soloLeague = null;
        LeagueEntry flexLeague = null;
        for (LeagueRecord current : currents) {
            LeagueEntry entry = entry(current, currentOow(current.wins(), current.losses()));
            if (isSolo(current)) {
                soloLeague = entry;
            } else {
                flexLeague = entry;
            }
        }

        List<LeagueEntry> soloLeagueHistory = new ArrayList<>();
        List<LeagueEntry> flexLeagueHistory = new ArrayList<>();
        for (LeagueRecord history : histories) {
            LeagueEntry entry = entry(history, ratio(history.wins(), history.losses()).toString());
            (isSolo(history) ? soloLeagueHistory : flexLeagueHistory).add(entry);
        }
        return new SummonerLeagueReadModel(soloLeague, flexLeague, soloLeagueHistory, flexLeagueHistory);
    }

    private static LeagueEntry entry(LeagueRecord record, String oow) {
        return new LeagueEntry(record.queue(), record.leaguePoints(), record.wins(), record.losses(), oow,
                record.tier(), record.rank());
    }

    private static boolean isSolo(LeagueRecord record) {
        return SOLO_QUEUE.equals(record.queue());
    }

    private static String currentOow(int wins, int losses) {
        return ratio(wins, losses).multiply(ONE_HUNDRED).toString();
    }

    /** 소수 둘째 자리 반올림. 0판이면 0 (lol-server 이력은 0판에서 0 나누기로 500 이 난다). */
    private static BigDecimal ratio(int wins, int losses) {
        int total = wins + losses;
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(wins).divide(new BigDecimal(total), 2, RoundingMode.HALF_UP);
    }
}
