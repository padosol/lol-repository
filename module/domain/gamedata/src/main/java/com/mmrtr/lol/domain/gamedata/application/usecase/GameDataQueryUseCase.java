package com.mmrtr.lol.domain.gamedata.application.usecase;

import com.mmrtr.lol.domain.gamedata.domain.QueueInfo;
import com.mmrtr.lol.domain.gamedata.readmodel.PatchNoteReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.PatchNoteSummaryReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.SeasonReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.VersionReadModel;
import java.util.List;

public interface GameDataQueryUseCase {

    /** 최신 패치 버전. 없으면 null. */
    VersionReadModel getLatestVersion();

    /** 화면에서 고르는 최근 패치 목록 (최신순). */
    List<VersionReadModel> getRecentVersions();

    /** 없으면 null (lol-server 와 같이 {@code data: null} 로 응답). */
    VersionReadModel getVersionById(Long versionId);

    List<SeasonReadModel> getAllSeasons();

    /** 없으면 null (lol-server 와 같이 {@code data: null} 로 응답). */
    SeasonReadModel getSeasonById(Long seasonId);

    List<PatchNoteSummaryReadModel> getAllPatchNotes();

    /** 없으면 {@code NOT_FOUND_PATCH_NOTE}. */
    PatchNoteReadModel getPatchNote(String versionId);

    /** @param tabOnly true 면 전적 탭으로 노출하는 큐만 */
    List<QueueInfo> getQueues(boolean tabOnly);
}
