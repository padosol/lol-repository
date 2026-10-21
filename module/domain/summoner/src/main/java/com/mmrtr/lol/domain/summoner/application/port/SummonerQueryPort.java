package com.mmrtr.lol.domain.summoner.application.port;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerLeagueReadModel.LeagueRecord;

import java.util.List;
import java.util.Optional;

/** 공개 소환사 조회 (`/api/v1/summoners/**`). 소환사·리그 테이블을 읽는 조회 전용 포트. */
public interface SummonerQueryPort {

    Optional<Summoner> findSummoner(String puuid);

    /** {@code search_name} 이 일치하는 소환사, 최대 limit 건. */
    List<Summoner> findSummonersBySearchName(String platformId, String searchName, int limit);

    /** {@code search_name} 이 prefix 로 시작하는 소환사, 최대 limit 건. prefix 의 LIKE 특수문자는 글자 그대로 찾는다. */
    List<Summoner> findSummonersBySearchNamePrefix(String platformId, String prefix, int limit);

    /**
     * 랭크 기록({@code league_summoner})이 있는 소환사 중 {@code search_name} 이 prefix 로 시작하는 소환사.
     * {@code search_name} 순으로 최대 limit 건, 리그 정보({@link Summoner#getLeagueInfos()})를 채워 돌려준다.
     */
    List<Summoner> findRankedSummonersBySearchNamePrefix(String platformId, String prefix, int limit);

    /** 현재 랭크 ({@code league_summoner}). */
    List<LeagueRecord> findLeagues(String puuid);

    /** 랭크 변동 이력 ({@code league_summoner_history}), 최신순. */
    List<LeagueRecord> findLeagueHistories(String puuid);
}
