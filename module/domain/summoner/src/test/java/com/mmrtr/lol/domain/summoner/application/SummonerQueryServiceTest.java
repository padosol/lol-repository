package com.mmrtr.lol.domain.summoner.application;

import com.mmrtr.lol.domain.summoner.application.port.SummonerApiPort;
import com.mmrtr.lol.domain.summoner.application.port.SummonerLookupLockPort;
import com.mmrtr.lol.domain.summoner.application.port.SummonerQueryPort;
import com.mmrtr.lol.domain.summoner.application.usecase.SaveSummonerDataUseCase;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.GameIdentity;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;
import com.mmrtr.lol.domain.summoner.domain.vo.RevisionInfo;
import com.mmrtr.lol.domain.summoner.domain.vo.StatusInfo;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerAutocompleteReadModel;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerLeagueReadModel;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerLeagueReadModel.LeagueRecord;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerReadModel;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SummonerQueryServiceTest {

    private static final String PUUID = "puuid-1";
    private static final LocalDateTime REVISION = LocalDateTime.of(2026, 3, 1, 12, 30, 5);
    private static final LocalDateTime RIOT_CALL = LocalDateTime.of(2026, 3, 2, 8, 0, 0);

    @Mock
    private SummonerQueryPort summonerQueryPort;

    @Mock
    private SummonerApiPort summonerApiPort;

    @Mock
    private SaveSummonerDataUseCase saveSummonerDataUseCase;

    @Mock
    private SummonerLookupLockPort summonerLookupLockPort;

    private SummonerQueryService service;

    @BeforeEach
    void setUp() {
        service = new SummonerQueryService(summonerQueryPort, summonerApiPort, saveSummonerDataUseCase,
                summonerLookupLockPort, Runnable::run);
    }

    @DisplayName("puuid 조회는 저장된 소환사를 lol-server 날짜 형식으로, platform 은 null 로 돌려준다")
    @Test
    void getSummonerByPuuid_storedSummoner_returnsLolServerShape() {
        // given
        given(summonerQueryPort.findSummoner(PUUID)).willReturn(Optional.of(summoner(PUUID, "hide on bush", "KR1")));

        // when
        SummonerReadModel result = service.getSummonerByPuuid(PUUID);

        // then
        assertThat(result).isEqualTo(new SummonerReadModel(6, PUUID, 700, "hide on bush", "KR1", null,
                "2026-03-01 12:30:05", "2026-03-02 08:00:00"));
    }

    @DisplayName("저장되지 않은 puuid 는 NOT_FOUND_PUUID — Riot 을 호출하지 않는다")
    @Test
    void getSummonerByPuuid_unknownPuuid_throwsNotFoundPuuid() {
        // given
        given(summonerQueryPort.findSummoner(PUUID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getSummonerByPuuid(PUUID))
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND_PUUID));
        verifyNoInteractions(summonerApiPort);
    }

    @DisplayName("Riot ID 는 공백을 빼고 소문자로 search_name 과 비교하고, 하나로 특정되면 Riot 을 호출하지 않는다")
    @Test
    void getSummonerByRiotId_uniqueStoredSummoner_returnsWithoutRiotCall() {
        // given
        given(summonerQueryPort.findSummonersBySearchName("KR", "hideonbush#kr1", 2))
                .willReturn(List.of(summoner(PUUID, "hide on bush", "KR1")));

        // when
        SummonerReadModel result = service.getSummonerByRiotId("Hide On Bush-KR1", "kr");

        // then
        assertThat(result.puuid()).isEqualTo(PUUID);
        verifyNoInteractions(summonerApiPort, summonerLookupLockPort, saveSummonerDataUseCase);
    }

    @DisplayName("모르는 platform 은 INVALID_INPUT 400 — 저장소를 보지 않는다")
    @Test
    void getSummonerByRiotId_unknownPlatform_throwsInvalidInput() {
        // when & then
        assertThatThrownBy(() -> service.getSummonerByRiotId("hide on bush-KR1", "kr1"))
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.INVALID_INPUT));
        verifyNoInteractions(summonerQueryPort);
    }

    @DisplayName("저장되지 않은 소환사는 락 안에서 Riot 에서 찾아 갱신 일시를 과거로 두고 저장한다")
    @Test
    void getSummonerByRiotId_notStored_fetchesFromRiotAndSaves() {
        // given
        given(summonerQueryPort.findSummonersBySearchName("KR", "hideonbush#kr1", 2)).willReturn(List.of());
        runLockedActions();
        Summoner fetched = summoner(PUUID, "hide on bush", "KR1");
        given(summonerApiPort.fetchSummonerByRiotId(eq("hide on bush"), eq("KR1"), eq("KR"), any()))
                .willReturn(CompletableFuture.completedFuture(fetched));

        // when
        SummonerReadModel result = service.getSummonerByRiotId("hide on bush-KR1", "kr");

        // then
        verify(summonerLookupLockPort).executeWithLock(eq("KR:hideonbush#kr1"), any());
        verify(saveSummonerDataUseCase).execute(fetched);
        assertThat(result.puuid()).isEqualTo(PUUID);
        assertThat(result.lastRevisionDateTime()).isEqualTo("2026-01-01 00:00:00");
        assertThat(result.lastRevisionClickDateTime()).isEqualTo("2026-01-01 00:00:00");
    }

    @DisplayName("락을 기다리는 동안 다른 요청이 저장했으면 Riot 을 호출하지 않는다")
    @Test
    void getSummonerByRiotId_savedWhileWaitingForLock_skipsRiotCall() {
        // given
        given(summonerQueryPort.findSummonersBySearchName("KR", "hideonbush#kr1", 2))
                .willReturn(List.of(), List.of(summoner(PUUID, "hide on bush", "KR1")));
        runLockedActions();

        // when
        SummonerReadModel result = service.getSummonerByRiotId("hide on bush-KR1", "kr");

        // then
        assertThat(result.puuid()).isEqualTo(PUUID);
        verifyNoInteractions(summonerApiPort, saveSummonerDataUseCase);
    }

    @DisplayName("같은 소환사를 조회 중이라 락을 못 잡으면 LOCK_ACQUISITION_FAILED 503 (lol-server 와 같다)")
    @Test
    void getSummonerByRiotId_lockNotAcquired_throwsLockAcquisitionFailed() {
        // given
        given(summonerQueryPort.findSummonersBySearchName(anyString(), anyString(), anyInt())).willReturn(List.of());
        given(summonerLookupLockPort.executeWithLock(anyString(), any())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getSummonerByRiotId("hide on bush-KR1", "kr"))
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.LOCK_ACQUISITION_FAILED));
        verifyNoInteractions(summonerApiPort);
    }

    @DisplayName("Riot 에 없는 소환사는 어댑터가 낸 NOT_FOUND_USER 를 그대로 던진다 (CompletionException 을 벗긴다)")
    @Test
    void getSummonerByRiotId_riotNotFound_throwsNotFoundUser() {
        // given
        given(summonerQueryPort.findSummonersBySearchName(anyString(), anyString(), anyInt())).willReturn(List.of());
        runLockedActions();
        given(summonerApiPort.fetchSummonerByRiotId(anyString(), anyString(), anyString(), any()))
                .willReturn(CompletableFuture.failedFuture(new CoreException(ErrorType.NOT_FOUND_USER)));

        // when & then
        assertThatThrownBy(() -> service.getSummonerByRiotId("nobody-KR1", "kr"))
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND_USER));
        verify(saveSummonerDataUseCase, never()).execute(any());
    }

    @DisplayName("같은 Riot ID 로 저장된 행이 여럿이면(개명 전 행) Riot 에서 현재 소환사를 찾는다")
    @Test
    void getSummonerByRiotId_multipleStoredRows_fetchesFromRiot() {
        // given
        given(summonerQueryPort.findSummonersBySearchName("KR", "hideonbush#kr1", 2))
                .willReturn(List.of(summoner("old", "hide on bush", "KR1"), summoner(PUUID, "hide on bush", "KR1")));
        runLockedActions();
        given(summonerApiPort.fetchSummonerByRiotId(anyString(), anyString(), anyString(), any()))
                .willReturn(CompletableFuture.completedFuture(summoner(PUUID, "hide on bush", "KR1")));

        // when
        SummonerReadModel result = service.getSummonerByRiotId("hide on bush-KR1", "kr");

        // then
        assertThat(result.puuid()).isEqualTo(PUUID);
        verify(saveSummonerDataUseCase).execute(any());
    }

    @DisplayName("태그 없이는 저장된 소환사를 이름#로 찾고, 하나로 특정되지 않으면 Riot 을 호출하지 않고 NOT_FOUND_USER")
    @Test
    void getSummonerByRiotId_noTagLine_searchesStoredOnly() {
        // given
        given(summonerQueryPort.findSummonersBySearchNamePrefix("KR", "hideonbush#", 2)).willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> service.getSummonerByRiotId("hide on bush", "kr"))
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND_USER));
        verifyNoInteractions(summonerApiPort, summonerLookupLockPort);
    }

    @DisplayName("자동완성은 정규화한 검색어로 랭크 소환사를 최대 20건 찾고, 솔로 랭크 티어를 우선 보여준다")
    @Test
    void autocomplete_query_returnsRankedSummonersWithSoloTierFirst() {
        // given
        Summoner ranked = summonerWithLeagues(Set.of(
                league("RANKED_FLEX_SR", "PLATINUM", "IV", 10),
                league("RANKED_SOLO_5x5", "GOLD", "II", 45)));
        given(summonerQueryPort.findRankedSummonersBySearchNamePrefix("KR", "hideon", 20)).willReturn(List.of(ranked));

        // when
        List<SummonerAutocompleteReadModel> result = service.autocomplete("Hide On", "kr");

        // then
        assertThat(result).containsExactly(
                new SummonerAutocompleteReadModel("hide on bush", "KR1", 6, 700, "GOLD", "II", 45));
    }

    @DisplayName("공백뿐인 검색어는 빈 목록 — 플랫폼 전체를 돌려주지 않는다")
    @Test
    void autocomplete_blankQuery_returnsEmptyWithoutQuery() {
        // when
        List<SummonerAutocompleteReadModel> result = service.autocomplete("  ", "kr");

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(summonerQueryPort);
    }

    @DisplayName("리그는 현재 랭크와 최신순 이력을 솔로·자유로 나눈다")
    @Test
    void getLeagues_storedRecords_splitsSoloAndFlex() {
        // given
        given(summonerQueryPort.findLeagues(PUUID)).willReturn(List.of(
                new LeagueRecord("RANKED_SOLO_5x5", 45, 10, 10, "GOLD", "II")));
        given(summonerQueryPort.findLeagueHistories(PUUID)).willReturn(List.of(
                new LeagueRecord("RANKED_SOLO_5x5", 30, 9, 9, "GOLD", "II")));

        // when
        SummonerLeagueReadModel result = service.getLeagues(PUUID);

        // then
        assertThat(result.soloLeague().oow()).isEqualTo("50.00");
        assertThat(result.flexLeague()).isNull();
        assertThat(result.soloLeagueHistory()).extracting(SummonerLeagueReadModel.LeagueEntry::oow)
                .containsExactly("0.50");
        assertThat(result.flexLeagueHistory()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private void runLockedActions() {
        given(summonerLookupLockPort.executeWithLock(anyString(), any()))
                .willAnswer(invocation -> Optional.of(((Supplier<Object>) invocation.getArgument(1)).get()));
    }

    private Summoner summoner(String puuid, String gameName, String tagLine) {
        return Summoner.builder()
                .puuid(puuid)
                .gameIdentity(new GameIdentity(gameName, tagLine))
                .platformId("KR")
                .statusInfo(new StatusInfo(6, 700))
                .revisionInfo(new RevisionInfo(REVISION, RIOT_CALL))
                .leagueInfos(Set.of())
                .build();
    }

    private Summoner summonerWithLeagues(Set<LeagueInfo> leagueInfos) {
        return Summoner.builder()
                .puuid(PUUID)
                .gameIdentity(new GameIdentity("hide on bush", "KR1"))
                .platformId("KR")
                .statusInfo(new StatusInfo(6, 700))
                .revisionInfo(new RevisionInfo(REVISION, RIOT_CALL))
                .leagueInfos(leagueInfos)
                .build();
    }

    private LeagueInfo league(String queueType, String tier, String rank, int leaguePoints) {
        return LeagueInfo.builder().queueType(queueType).tier(tier).rank(rank).leaguePoints(leaguePoints).build();
    }
}
