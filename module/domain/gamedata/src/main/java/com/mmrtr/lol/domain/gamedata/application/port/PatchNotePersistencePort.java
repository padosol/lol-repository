package com.mmrtr.lol.domain.gamedata.application.port;

import com.mmrtr.lol.domain.gamedata.readmodel.PatchNoteReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.PatchNoteSummaryReadModel;
import java.util.List;
import java.util.Optional;

public interface PatchNotePersistencePort {

    /** 작성일 최신순. */
    List<PatchNoteSummaryReadModel> findAllSummary();

    Optional<PatchNoteReadModel> findByVersionId(String versionId);
}
