package com.mmrtr.lol.domain.match.readmodel;

import com.mmrtr.lol.common.type.Queue;

import java.util.ArrayList;
import java.util.List;

/** 솔로·자유 랭크의 챔피언별 통계 (소환사 모스트 챔피언). 필드가 lol-server {@code RankChampionsResponse} 와 같다. */
public record RankChampionsReadModel(List<RankChampion> solo, List<RankChampion> flex) {

    /**
     * 챔피언 하나의 통계. 평균은 반올림하지 않는다 (lol-server 와 같다).
     *
     * @param winRate 승률(%) — 승리 수 × 100 / 판 수
     */
    public record RankChampion(
            Double assists,
            Double deaths,
            Double kills,
            int championId,
            String championName,
            Long win,
            Long losses,
            Double winRate,
            Double damagePerMinute,
            Double kda,
            Double laneMinionsFirst10Minutes,
            Double damageTakenOnTeamPercentage,
            Double goldPerMinute,
            Long playCount
    ) {

        /** 승률을 판 수로 계산해 만든다. 판 수가 0 이면 승률 0. */
        public static RankChampion of(Double assists, Double deaths, Double kills, int championId, String championName,
                                      long win, long losses, Double damagePerMinute, Double kda,
                                      Double laneMinionsFirst10Minutes, Double damageTakenOnTeamPercentage,
                                      Double goldPerMinute, long playCount) {
            double winRate = playCount > 0 ? win * 100.0 / playCount : 0.0;
            return new RankChampion(assists, deaths, kills, championId, championName, win, losses, winRate,
                    damagePerMinute, kda, laneMinionsFirst10Minutes, damageTakenOnTeamPercentage, goldPerMinute,
                    playCount);
        }
    }

    /** 저장된 큐·챔피언별 집계 한 행. */
    public record RankChampionRecord(int queueId, RankChampion champion) {
    }

    /** 큐별로 나눈다. 순서는 그대로 둔다 (판 수 내림차순으로 넘긴다). 솔로·자유 랭크가 아닌 큐는 버린다. */
    public static RankChampionsReadModel of(List<RankChampionRecord> records) {
        List<RankChampion> solo = new ArrayList<>();
        List<RankChampion> flex = new ArrayList<>();
        for (RankChampionRecord championRecord : records) {
            if (championRecord.queueId() == Queue.RANKED_SOLO_5x5.getQueueId()) {
                solo.add(championRecord.champion());
            } else if (championRecord.queueId() == Queue.RANKED_FLEX_SR.getQueueId()) {
                flex.add(championRecord.champion());
            }
        }
        return new RankChampionsReadModel(solo, flex);
    }
}
