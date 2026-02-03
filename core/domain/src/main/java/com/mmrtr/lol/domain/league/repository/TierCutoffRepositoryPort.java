package com.mmrtr.lol.domain.league.repository;

import com.mmrtr.lol.domain.league.domain.TierCutoff;

import java.util.List;

public interface TierCutoffRepositoryPort {

    void saveAll(List<TierCutoff> cutoffs);

    List<TierCutoff> findByQueue(String queue);

    void backupCurrentCutoffs(String queue);

    void updateLpChangesFromBackup(String queue);

    void clearBackup(String queue);
}
