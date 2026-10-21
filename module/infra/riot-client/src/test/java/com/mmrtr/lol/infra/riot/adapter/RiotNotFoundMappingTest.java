package com.mmrtr.lol.infra.riot.adapter;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Riot 404({@link RiotClientNotFoundException})가 어댑터의 404 분기로 가는지 — 전에는 500 이 됐다. */
class RiotNotFoundMappingTest {

    private static final Executor DIRECT = Runnable::run;

    private final RiotApiService riotApiService = mock(RiotApiService.class);

    @DisplayName("없는 Riot ID 는 NOT_FOUND_USER")
    @Test
    void fetchSummonerByRiotId_riot404_throwsNotFoundUser() {
        // given
        when(riotApiService.getAccountByRiotId(anyString(), anyString(), any(Platform.class), any()))
                .thenReturn(CompletableFuture.failedFuture(notFound()));
        SummonerApiAdapter adapter = new SummonerApiAdapter(riotApiService);

        // when & then
        assertThatThrownBy(() -> adapter.fetchSummonerByRiotId("nobody", "KR1", "KR", DIRECT).join())
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND_USER));
    }

    @DisplayName("게임 중이 아니면(관전 404) empty")
    @Test
    void fetchActiveGameByPuuid_riot404_returnsEmpty() {
        // given
        when(riotApiService.getActiveGameByPuuid(anyString(), any(Platform.class), any()))
                .thenReturn(CompletableFuture.failedFuture(notFound()));
        SpectatorApiAdapter adapter = new SpectatorApiAdapter(riotApiService);

        // when & then
        assertThat(adapter.fetchActiveGameByPuuid("puuid-1", "KR", DIRECT).join()).isEmpty();
    }

    private RiotClientNotFoundException notFound() {
        return new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "Not Found", LogLevel.WARN);
    }
}
