package com.mmrtr.lol.domain.spectator.application;

import com.mmrtr.lol.domain.spectator.application.port.ActiveGameCachePort;
import com.mmrtr.lol.domain.spectator.application.port.SpectatorApiPort;
import com.mmrtr.lol.domain.spectator.application.port.SpectatorTargetPort;
import com.mmrtr.lol.domain.spectator.domain.ActiveGame;
import com.mmrtr.lol.domain.spectator.domain.BannedChampion;
import com.mmrtr.lol.domain.spectator.domain.GameParticipant;
import com.mmrtr.lol.domain.spectator.domain.GamePerks;
import com.mmrtr.lol.domain.spectator.domain.SpectatorTarget;
import com.mmrtr.lol.domain.spectator.readmodel.ActiveGameReadModel;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ActiveGameQueryServiceTest {

    private static final String PUUID = "puuid-1";
    private static final long GAME_START = 1_706_400_000_000L;
    private static final LocalDateTime BEFORE_GAME = at(GAME_START).minusMinutes(5);
    private static final LocalDateTime AFTER_GAME = at(GAME_START).plusMinutes(40);

    @Mock
    private SpectatorTargetPort spectatorTargetPort;

    @Mock
    private ActiveGameCachePort activeGameCachePort;

    @Mock
    private SpectatorApiPort spectatorApiPort;

    private ActiveGameQueryService service;

    @BeforeEach
    void setUp() {
        service = new ActiveGameQueryService(spectatorTargetPort, activeGameCachePort, spectatorApiPort, Runnable::run);
    }

    @DisplayName("저장되지 않은 puuid 는 NOT_FOUND_PUUID")
    @Test
    void getActiveGame_unknownPuuid_throwsNotFoundPuuid() {
        // given
        given(spectatorTargetPort.findTarget(PUUID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getActiveGame(PUUID))
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND_PUUID));
    }

    @DisplayName("저장된 platform_id 가 없거나 모르는 값이면 UNKNOWN_SUMMONER_PLATFORM — 캐시·Riot 을 보지 않는다")
    @Test
    void getActiveGame_unknownPlatform_throwsUnknownSummonerPlatform() {
        // given
        given(spectatorTargetPort.findTarget(PUUID)).willReturn(Optional.of(new SpectatorTarget(null, BEFORE_GAME)));

        // when & then
        assertThatThrownBy(() -> service.getActiveGame(PUUID))
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.UNKNOWN_SUMMONER_PLATFORM));
        verifyNoInteractions(activeGameCachePort, spectatorApiPort);
    }

    @DisplayName("캐시된 게임은 게임 시작 뒤 갱신이 없으면 그대로 돌려준다")
    @Test
    void getActiveGame_cachedAndNotRenewed_returnsCached() {
        // given
        given(spectatorTargetPort.findTarget(PUUID)).willReturn(Optional.of(new SpectatorTarget("KR", BEFORE_GAME)));
        ActiveGameReadModel cached = ActiveGameReadModel.from(game());
        given(activeGameCachePort.findGame("KR", PUUID)).willReturn(Optional.of(cached));

        // when & then
        assertThat(service.getActiveGame(PUUID)).contains(cached);
        verifyNoInteractions(spectatorApiPort);
    }

    @DisplayName("게임 시작 뒤 소환사가 갱신됐으면 참여자 캐시를 지우고 Riot 에서 다시 찾는다")
    @Test
    void getActiveGame_cachedButRenewedAfterStart_evictsAndRefetches() {
        // given
        given(spectatorTargetPort.findTarget(PUUID)).willReturn(Optional.of(new SpectatorTarget("KR", AFTER_GAME)));
        ActiveGameReadModel cached = ActiveGameReadModel.from(game());
        given(activeGameCachePort.findGame("KR", PUUID)).willReturn(Optional.of(cached));
        given(spectatorApiPort.fetchActiveGameByPuuid(eq(PUUID), eq("KR"), any()))
                .willReturn(CompletableFuture.completedFuture(Optional.empty()));

        // when & then
        assertThat(service.getActiveGame(PUUID)).isEmpty();
        verify(activeGameCachePort).evictGame("KR", cached);
        verify(activeGameCachePort).saveNotInGame("KR", PUUID);
    }

    @DisplayName("최근에 게임 중이 아니었으면 Riot 을 부르지 않는다")
    @Test
    void getActiveGame_notInGameCached_returnsEmptyWithoutRiotCall() {
        // given
        given(spectatorTargetPort.findTarget(PUUID)).willReturn(Optional.of(new SpectatorTarget("KR", BEFORE_GAME)));
        given(activeGameCachePort.findGame("KR", PUUID)).willReturn(Optional.empty());
        given(activeGameCachePort.isNotInGame("KR", PUUID)).willReturn(true);

        // when & then
        assertThat(service.getActiveGame(PUUID)).isEmpty();
        verifyNoInteractions(spectatorApiPort);
    }

    @DisplayName("NA1 소환사는 Riot 을 플랫폼 이름 NA 로 부르고, 찾은 게임을 platform_id 키로 캐싱한다")
    @Test
    void getActiveGame_inGame_fetchesWithPlatformNameAndCaches() {
        // given
        given(spectatorTargetPort.findTarget(PUUID)).willReturn(Optional.of(new SpectatorTarget("NA1", BEFORE_GAME)));
        given(activeGameCachePort.findGame("NA1", PUUID)).willReturn(Optional.empty());
        given(activeGameCachePort.isNotInGame("NA1", PUUID)).willReturn(false);
        given(spectatorApiPort.fetchActiveGameByPuuid(eq(PUUID), eq("NA"), any()))
                .willReturn(CompletableFuture.completedFuture(Optional.of(game())));

        // when
        Optional<ActiveGameReadModel> result = service.getActiveGame(PUUID);

        // then
        assertThat(result).get().satisfies(game -> {
            assertThat(game.encryptionKey()).isEqualTo("key-123");
            assertThat(game.participants()).extracting(ActiveGameReadModel.Participant::isBot)
                    .containsExactly(false, true);
            assertThat(game.bannedChampions()).hasSize(1);
        });
        verify(activeGameCachePort).saveGame("NA1", result.get());
    }

    @DisplayName("Riot 조회가 실패하면 게임 없음으로 응답하고 캐싱하지 않는다 (lol-server 와 같다)")
    @Test
    void getActiveGame_riotFailure_returnsEmptyWithoutCaching() {
        // given
        given(spectatorTargetPort.findTarget(PUUID)).willReturn(Optional.of(new SpectatorTarget("KR", BEFORE_GAME)));
        given(activeGameCachePort.findGame("KR", PUUID)).willReturn(Optional.empty());
        given(spectatorApiPort.fetchActiveGameByPuuid(anyString(), anyString(), any()))
                .willReturn(CompletableFuture.failedFuture(new IllegalStateException("riot 503")));

        // when & then
        assertThat(service.getActiveGame(PUUID)).isEmpty();
        verify(activeGameCachePort, never()).saveNotInGame(anyString(), anyString());
        verify(activeGameCachePort, never()).saveGame(anyString(), any());
    }

    private ActiveGame game() {
        GamePerks perks = GamePerks.builder().perkStyle(8100).perkSubStyle(8300).perkIds(List.of(8112L, 8126L)).build();
        return ActiveGame.builder()
                .gameId(12345L)
                .gameType("MATCHED_GAME")
                .gameMode("CLASSIC")
                .mapId(11)
                .gameQueueConfigId(420)
                .gameStartTime(GAME_START)
                .gameLength(600)
                .platformId("KR")
                .encryptionKey("key-123")
                .participants(List.of(
                        GameParticipant.builder().riotId("p1#KR1").puuid(PUUID).championId(1).teamId(100)
                                .spell1Id(4).spell2Id(14).profileIconId(1).bot(false).perks(perks).build(),
                        GameParticipant.builder().riotId("bot#KR1").puuid("puuid-2").championId(2).teamId(200)
                                .spell1Id(7).spell2Id(11).profileIconId(2).bot(true).perks(perks).build()))
                .bannedChampions(List.of(BannedChampion.builder().championId(157).teamId(100).pickTurn(1).build()))
                .build();
    }

    private static LocalDateTime at(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }
}
