package com.mmrtr.lol.domain.gamedata.application;

import com.mmrtr.lol.domain.gamedata.application.port.PatchNotePersistencePort;
import com.mmrtr.lol.domain.gamedata.application.port.QueuePersistencePort;
import com.mmrtr.lol.domain.gamedata.application.port.SeasonPersistencePort;
import com.mmrtr.lol.domain.gamedata.application.port.VersionCachePort;
import com.mmrtr.lol.domain.gamedata.application.port.VersionPersistencePort;
import com.mmrtr.lol.domain.gamedata.readmodel.VersionReadModel;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GameDataQueryServiceTest {

    private final VersionPersistencePort versionPersistencePort = mock(VersionPersistencePort.class);
    private final VersionCachePort versionCachePort = mock(VersionCachePort.class);
    private final PatchNotePersistencePort patchNotePersistencePort = mock(PatchNotePersistencePort.class);
    private final QueuePersistencePort queuePersistencePort = mock(QueuePersistencePort.class);

    private final GameDataQueryService service = new GameDataQueryService(
            versionPersistencePort, versionCachePort, mock(SeasonPersistencePort.class),
            patchNotePersistencePort, queuePersistencePort);

    private final VersionReadModel latest =
            new VersionReadModel(3L, "16.16", "16.16.1", LocalDateTime.of(2026, 8, 13, 10, 0));

    @DisplayName("최신 버전: 캐시에 있으면 DB 를 조회하지 않는다")
    @Test
    void latestVersionFromCache() {
        given(versionCachePort.findLatestVersion()).willReturn(Optional.of(latest));

        assertThat(service.getLatestVersion()).isEqualTo(latest);
        verify(versionPersistencePort, never()).findLatestVersion();
    }

    @DisplayName("최신 버전: 캐시 미스면 DB 에서 읽어 캐시에 넣는다")
    @Test
    void latestVersionCacheMiss() {
        given(versionCachePort.findLatestVersion()).willReturn(Optional.empty());
        given(versionPersistencePort.findLatestVersion()).willReturn(Optional.of(latest));

        assertThat(service.getLatestVersion()).isEqualTo(latest);
        verify(versionCachePort).saveLatestVersion(latest);
    }

    @DisplayName("최신 버전: DB 에도 없으면 null 이고 캐시하지 않는다")
    @Test
    void latestVersionMissing() {
        given(versionCachePort.findLatestVersion()).willReturn(Optional.empty());
        given(versionPersistencePort.findLatestVersion()).willReturn(Optional.empty());

        assertThat(service.getLatestVersion()).isNull();
        verify(versionCachePort, never()).saveLatestVersion(any());
    }

    @DisplayName("없는 버전 단건은 null (lol-server 와 같이 data=null)")
    @Test
    void versionByIdMissing() {
        given(versionPersistencePort.findVersionById(9L)).willReturn(Optional.empty());

        assertThat(service.getVersionById(9L)).isNull();
    }

    @DisplayName("없는 패치노트는 NOT_FOUND_PATCH_NOTE")
    @Test
    void patchNoteMissing() {
        given(patchNotePersistencePort.findByVersionId("x")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPatchNote("x"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.NOT_FOUND_PATCH_NOTE);
    }

    @DisplayName("큐: tab=true 면 탭 큐만, 아니면 전체")
    @Test
    void queues() {
        service.getQueues(true);
        verify(queuePersistencePort).findAllTabs();

        service.getQueues(false);
        verify(queuePersistencePort).findAll();
    }
}
