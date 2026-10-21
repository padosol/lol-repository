package com.mmrtr.lol.domain.gamedata.application.port;

import com.mmrtr.lol.domain.gamedata.readmodel.VersionReadModel;
import java.util.List;
import java.util.Optional;

public interface VersionPersistencePort {

    Optional<VersionReadModel> findLatestVersion();

    /** 최신 버전부터 최대 {@code limit} 개. */
    List<VersionReadModel> findRecentVersions(int limit);

    Optional<VersionReadModel> findVersionById(Long versionId);
}
