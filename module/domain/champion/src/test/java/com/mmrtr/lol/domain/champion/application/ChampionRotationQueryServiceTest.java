package com.mmrtr.lol.domain.champion.application;

import com.mmrtr.lol.domain.champion.application.port.ChampionApiPort;
import com.mmrtr.lol.domain.champion.application.port.ChampionRotationCachePort;
import com.mmrtr.lol.domain.champion.domain.ChampionRotation;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ChampionRotationQueryServiceTest {

    private final ChampionApiPort championApiPort = mock(ChampionApiPort.class);
    private final ChampionRotationCachePort cachePort = mock(ChampionRotationCachePort.class);
    private final ChampionRotationQueryService service =
            new ChampionRotationQueryService(championApiPort, cachePort, Runnable::run);

    private final ChampionRotation rotation = new ChampionRotation(List.of(1, 2), List.of(101), 10);

    @DisplayName("캐시에 있으면 Riot 을 호출하지 않는다")
    @Test
    void cacheHit() {
        given(cachePort.find("kr")).willReturn(Optional.of(rotation));

        assertThat(service.getChampionRotation("kr")).isSameAs(rotation);
        verify(championApiPort, never()).fetchChampionRotation(anyString(), any(Executor.class));
    }

    @DisplayName("캐시 미스면 Riot 에서 받아 캐시한다")
    @Test
    void cacheMiss() {
        given(cachePort.find("kr")).willReturn(Optional.empty());
        given(championApiPort.fetchChampionRotation(eq("kr"), any(Executor.class)))
                .willReturn(CompletableFuture.completedFuture(rotation));

        assertThat(service.getChampionRotation("kr")).isSameAs(rotation);
        verify(cachePort).save("kr", rotation);
    }

    @DisplayName("Riot 조회 실패면 빈 로테이션을 반환·캐시한다 (어댑터가 짧은 TTL 적용)")
    @Test
    void riotFailure() {
        given(cachePort.find("kr")).willReturn(Optional.empty());
        given(championApiPort.fetchChampionRotation(eq("kr"), any(Executor.class)))
                .willReturn(CompletableFuture.failedFuture(new IllegalStateException("riot down")));

        ChampionRotation result = service.getChampionRotation("kr");

        assertThat(result.isEmpty()).isTrue();
        ArgumentCaptor<ChampionRotation> saved = ArgumentCaptor.forClass(ChampionRotation.class);
        verify(cachePort).save(eq("kr"), saved.capture());
        assertThat(saved.getValue().isEmpty()).isTrue();
    }

    @DisplayName("없는 platform 은 INVALID_INPUT, 캐시·Riot 을 건드리지 않는다")
    @Test
    void invalidPlatform() {
        assertThatThrownBy(() -> service.getChampionRotation("mars"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.INVALID_INPUT);
        verifyNoInteractions(cachePort, championApiPort);
    }
}
