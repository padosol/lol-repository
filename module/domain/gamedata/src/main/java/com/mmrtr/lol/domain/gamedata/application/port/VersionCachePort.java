package com.mmrtr.lol.domain.gamedata.application.port;

import com.mmrtr.lol.domain.gamedata.readmodel.VersionReadModel;
import java.util.Optional;

public interface VersionCachePort {

    Optional<VersionReadModel> findLatestVersion();

    void saveLatestVersion(VersionReadModel version);
}
