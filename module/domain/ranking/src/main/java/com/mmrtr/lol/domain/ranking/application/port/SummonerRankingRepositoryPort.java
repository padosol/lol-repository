package com.mmrtr.lol.domain.ranking.application.port;

import com.mmrtr.lol.domain.ranking.domain.SummonerRanking;

import java.util.List;

public interface SummonerRankingRepositoryPort {

    List<SummonerRanking> saveAll(List<SummonerRanking> rankings);

    void bulkSaveAll(List<SummonerRanking> rankings);

    void deleteByQueue(String queue);

    void backupCurrentRanks(String queue);

    void updateRankChangesFromBackup(String queue);

    void clearBackup(String queue);

    void replaceAllRankings(String queue, List<SummonerRanking> rankings);
}
