package com.mmrtr.lol.domain.league.readmodel;

import com.mmrtr.lol.domain.league.domain.SummonerRanking;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** 리더보드 한 줄. lol-server {@code RankReadModel} 과 필드가 같아야 한다 ({@code RankingControllerTest} 가 검증). */
public record RankingReadModel(
        String puuid,
        int currentRank,
        int rankChange,
        String gameName,
        String tagLine,
        int wins,
        int losses,
        BigDecimal winRate,
        String tier,
        String rank,
        int leaguePoints,
        List<String> champions
) {
    public static RankingReadModel from(SummonerRanking ranking) {
        return new RankingReadModel(
                ranking.getPuuid(),
                ranking.getCurrentRank(),
                ranking.getRankChange(),
                ranking.getGameName(),
                ranking.getTagLine(),
                ranking.getWins(),
                ranking.getLosses(),
                ranking.getWinRate(),
                ranking.getTier(),
                ranking.getRank(),
                ranking.getLeaguePoints(),
                mostChampions(ranking));
    }

    /** 모스트 챔피언 1~3 중 비어 있지 않은 것만 순서대로 (lol-server {@code RankMapper.mapChampions} 와 같다). */
    private static List<String> mostChampions(SummonerRanking ranking) {
        return Stream.of(ranking.getMostChampion1(), ranking.getMostChampion2(), ranking.getMostChampion3())
                .filter(Objects::nonNull)
                .filter(champion -> !champion.isEmpty())
                .toList();
    }
}
