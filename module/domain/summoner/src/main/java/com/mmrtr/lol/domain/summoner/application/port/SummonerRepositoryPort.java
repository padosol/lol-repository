package com.mmrtr.lol.domain.summoner.application.port;

import com.mmrtr.lol.domain.summoner.domain.Summoner;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface SummonerRepositoryPort {

    void save(Summoner summoner);

    Optional<Summoner> findByPuuid(String puuid);

    Map<String, Summoner> findAllByPuuidIn(Collection<String> puuids);

    void updateLastRiotCallDate(String puuid);

    /** 아이콘·레벨만 바꾼다. 행이 없으면 false. */
    boolean updateStatusInfo(String puuid, int profileIconId, long summonerLevel);

    /** 게임 이름·태그와 searchName 만 바꾼다. */
    void updateGameIdentity(String puuid, String gameName, String tagLine);

    /** revisionDate 만 바꾼다. */
    void updateRevisionDate(String puuid, LocalDateTime revisionDate);
}
