package com.mmrtr.lol.domain.spectator.application;

import com.mmrtr.lol.domain.spectator.application.port.SpectatorApiPort;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SpectatorServiceTest {

    private final SpectatorApiPort spectatorApiPort = mock(SpectatorApiPort.class);
    private final RiotPermitPort riotPermitPort = mock(RiotPermitPort.class);
    private final SpectatorService service = new SpectatorService(spectatorApiPort, riotPermitPort, Runnable::run);

    @Test
    @DisplayName("허가가 거절되면 Riot 을 부르지 않고 TOO_MANY_REQUESTS 를 던진다")
    void deniedThrowsTooManyRequests() {
        // given
        given(riotPermitPort.tryAcquire(anyString(), any(), anyMap())).willReturn(Optional.empty());

        // when // then
        assertThatThrownBy(() -> service.getActiveGameByPuuid("KR", "p1"))
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.TOO_MANY_REQUESTS));
        verifyNoInteractions(spectatorApiPort);
    }
}
