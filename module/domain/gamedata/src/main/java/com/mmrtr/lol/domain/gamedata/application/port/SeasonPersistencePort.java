package com.mmrtr.lol.domain.gamedata.application.port;

import com.mmrtr.lol.domain.gamedata.readmodel.SeasonReadModel;
import java.util.List;
import java.util.Optional;

public interface SeasonPersistencePort {

    /** 시즌 최신순, 시즌별 패치 버전도 최신순. */
    List<SeasonReadModel> findAllSeasons();

    Optional<SeasonReadModel> findSeasonById(Long seasonId);
}
