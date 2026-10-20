package com.mmrtr.lol.domain.match.application.port;

import com.mmrtr.lol.domain.match.readmodel.DailyGameCountReadModel.DailyCount;
import com.mmrtr.lol.domain.match.readmodel.RankChampionsReadModel.RankChampionRecord;

import java.time.LocalDateTime;
import java.util.List;

/** 소환사별 매치 통계 (일별 게임 수·모스트 챔피언). 매치·참가자·challenges 테이블을 집계하는 조회 전용 포트. */
public interface MatchStatsQueryPort {

    /** 소환사의 날짜별 게임 수, 날짜 오름차순. 게임 생성 일시가 from 이후인 매치만 센다. */
    List<DailyCount> findDailyGameCounts(String puuid, int season, List<Integer> queueIds, LocalDateTime from);

    /**
     * 솔로·자유 랭크의 큐·챔피언별 집계, 판 수 내림차순. challenges 행이 있는 게임만 센다 (lol-server 와 같다).
     *
     * @param season null 이면 전 시즌
     */
    List<RankChampionRecord> findRankChampions(String puuid, Integer season);
}
