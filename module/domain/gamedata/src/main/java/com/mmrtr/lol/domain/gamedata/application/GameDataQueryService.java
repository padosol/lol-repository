package com.mmrtr.lol.domain.gamedata.application;

import com.mmrtr.lol.domain.gamedata.application.port.PatchNotePersistencePort;
import com.mmrtr.lol.domain.gamedata.application.port.QueuePersistencePort;
import com.mmrtr.lol.domain.gamedata.application.port.SeasonPersistencePort;
import com.mmrtr.lol.domain.gamedata.application.port.VersionCachePort;
import com.mmrtr.lol.domain.gamedata.application.port.VersionPersistencePort;
import com.mmrtr.lol.domain.gamedata.application.usecase.GameDataQueryUseCase;
import com.mmrtr.lol.domain.gamedata.domain.QueueInfo;
import com.mmrtr.lol.domain.gamedata.readmodel.PatchNoteReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.PatchNoteSummaryReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.SeasonReadModel;
import com.mmrtr.lol.domain.gamedata.readmodel.VersionReadModel;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 서비스 단위 트랜잭션을 두지 않는다 — 각 조회는 리포지토리 호출 한두 번이고 지연 로딩이 없어,
 * 리포지토리 메서드 자체의 읽기 전용 트랜잭션으로 충분하다. 클래스에 걸면 최신 버전 캐시 히트에도
 * DB 커넥션을 잡는다.
 */
@Service
@RequiredArgsConstructor
public class GameDataQueryService implements GameDataQueryUseCase {

    /**
     * 버전 목록 노출 상한. 화면에서 고르는 대상은 현재 패치와 직전 패치뿐이라
     * 시즌이 쌓여도 목록이 길어지지 않도록 여기서 자른다.
     */
    private static final int RECENT_VERSION_LIMIT = 2;

    private final VersionPersistencePort versionPersistencePort;
    private final VersionCachePort versionCachePort;
    private final SeasonPersistencePort seasonPersistencePort;
    private final PatchNotePersistencePort patchNotePersistencePort;
    private final QueuePersistencePort queuePersistencePort;

    @Override
    public VersionReadModel getLatestVersion() {
        return versionCachePort.findLatestVersion()
                .orElseGet(() -> versionPersistencePort.findLatestVersion()
                        .map(version -> {
                            versionCachePort.saveLatestVersion(version);
                            return version;
                        })
                        .orElse(null));
    }

    @Override
    public List<VersionReadModel> getRecentVersions() {
        return versionPersistencePort.findRecentVersions(RECENT_VERSION_LIMIT);
    }

    @Override
    public VersionReadModel getVersionById(Long versionId) {
        return versionPersistencePort.findVersionById(versionId).orElse(null);
    }

    @Override
    public List<SeasonReadModel> getAllSeasons() {
        return seasonPersistencePort.findAllSeasons();
    }

    @Override
    public SeasonReadModel getSeasonById(Long seasonId) {
        return seasonPersistencePort.findSeasonById(seasonId).orElse(null);
    }

    @Override
    public List<PatchNoteSummaryReadModel> getAllPatchNotes() {
        return patchNotePersistencePort.findAllSummary();
    }

    @Override
    public PatchNoteReadModel getPatchNote(String versionId) {
        return patchNotePersistencePort.findByVersionId(versionId)
                .orElseThrow(() -> new CoreException(
                        ErrorType.NOT_FOUND_PATCH_NOTE,
                        "존재하지 않는 패치노트입니다. versionId: " + versionId));
    }

    @Override
    public List<QueueInfo> getQueues(boolean tabOnly) {
        return tabOnly ? queuePersistencePort.findAllTabs() : queuePersistencePort.findAll();
    }
}
