package com.mmrtr.lol.domain.champion.application;

import com.mmrtr.lol.domain.champion.application.port.ChampionApiPort;
import com.mmrtr.lol.domain.champion.domain.ChampionRotation;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermit;
import com.mmrtr.lol.support.riot.RiotPermitDeniedException;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ChampionRotateServiceTest {

    private final ChampionApiPort championApiPort = mock(ChampionApiPort.class);
    private final RiotPermitPort riotPermitPort = mock(RiotPermitPort.class);
    private final ChampionRotateService service = new ChampionRotateService(championApiPort, riotPermitPort, Runnable::run);

    @Test
    @DisplayName("USER 로 로테이션 허가 1건을 받아 그 허가로 부른다")
    void getChampionRotate_acquiresUserPermit() {
        // given
        RiotPermits permits = RiotPermits.of(List.of(
                new RiotPermit("t-1", "kr", RiotMethod.CHAMPION_ROTATION, RiotCallPriority.USER)));
        given(riotPermitPort.tryAcquire("kr", RiotCallPriority.USER, Map.of(RiotMethod.CHAMPION_ROTATION, 1)))
                .willReturn(Optional.of(permits));
        ChampionRotation rotation = new ChampionRotation(List.of(1), List.of(2), 10);
        given(championApiPort.fetchChampionRotation(eq("kr"), eq(permits), any()))
                .willReturn(CompletableFuture.completedFuture(rotation));

        // when // then
        assertThat(service.getChampionRotate("kr")).isSameAs(rotation);
    }

    @Test
    @DisplayName("허가가 거절되면 Riot 을 부르지 않고 RiotPermitDeniedException 이 올라간다")
    void getChampionRotate_permitDenied_throws() {
        // given
        given(riotPermitPort.tryAcquire(anyString(), any(), anyMap())).willReturn(Optional.empty());

        // when // then
        assertThatThrownBy(() -> service.getChampionRotate("kr")).isInstanceOf(RiotPermitDeniedException.class);
        verifyNoInteractions(championApiPort);
    }
}
