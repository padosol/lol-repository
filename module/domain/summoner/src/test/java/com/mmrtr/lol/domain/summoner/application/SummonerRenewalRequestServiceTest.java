package com.mmrtr.lol.domain.summoner.application;

import com.mmrtr.lol.domain.league.application.port.SummonerCollectionPublishPort;
import com.mmrtr.lol.domain.summoner.application.port.SummonerQueryPort;
import com.mmrtr.lol.domain.summoner.application.port.SummonerRenewalStatePort;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.GameIdentity;
import com.mmrtr.lol.domain.summoner.domain.vo.RevisionInfo;
import com.mmrtr.lol.domain.summoner.domain.vo.StatusInfo;
import com.mmrtr.lol.domain.summoner.readmodel.SummonerRenewalReadModel;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SummonerRenewalRequestServiceTest {

    private static final String PUUID = "puuid-1";

    @Mock
    private SummonerQueryPort summonerQueryPort;

    @Mock
    private SummonerRenewalStatePort summonerRenewalStatePort;

    @Mock
    private SummonerCollectionPublishPort summonerCollectionPublishPort;

    @InjectMocks
    private SummonerRenewalRequestService service;

    @DisplayName("저장되지 않은 puuid 는 NOT_FOUND_PUUID — 쿨다운을 걸지 않는다")
    @Test
    void requestRenewal_unknownPuuid_throwsNotFoundPuuid() {
        // given
        given(summonerQueryPort.findSummoner(PUUID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.requestRenewal(PUUID))
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.NOT_FOUND_PUUID));
        verifyNoInteractions(summonerRenewalStatePort, summonerCollectionPublishPort);
    }

    @DisplayName("플랫폼을 모르는 소환사는 쿨다운을 걸기 전에 UNKNOWN_SUMMONER_PLATFORM")
    @Test
    void requestRenewal_unknownPlatform_throwsBeforeCooldown() {
        // given
        given(summonerQueryPort.findSummoner(PUUID)).willReturn(Optional.of(summoner(" ", null)));

        // when & then
        assertThatThrownBy(() -> service.requestRenewal(PUUID))
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.UNKNOWN_SUMMONER_PLATFORM));
        verifyNoInteractions(summonerRenewalStatePort, summonerCollectionPublishPort);
    }

    @DisplayName("이미 갱신 중이면 SUCCESS — 쿨다운도 발행도 하지 않는다")
    @Test
    void requestRenewal_alreadyRenewing_returnsSuccessWithoutPublishing() {
        // given
        given(summonerQueryPort.findSummoner(PUUID)).willReturn(Optional.of(summoner("KR", null)));
        given(summonerRenewalStatePort.isRenewing(PUUID)).willReturn(true);

        // when
        SummonerRenewalReadModel result = service.requestRenewal(PUUID);

        // then
        assertThat(result).isEqualTo(new SummonerRenewalReadModel(PUUID, "SUCCESS"));
        verify(summonerRenewalStatePort, never()).tryStartClickCooldown(anyString());
        verifyNoInteractions(summonerCollectionPublishPort);
    }

    @DisplayName("클릭 쿨다운(10초) 중이면 FAILED")
    @Test
    void requestRenewal_clickCooldown_returnsFailed() {
        // given
        given(summonerQueryPort.findSummoner(PUUID)).willReturn(Optional.of(summoner("KR", null)));
        given(summonerRenewalStatePort.tryStartClickCooldown(PUUID)).willReturn(false);

        // when
        SummonerRenewalReadModel result = service.requestRenewal(PUUID);

        // then
        assertThat(result.rejected()).isTrue();
        verify(summonerRenewalStatePort, never()).markRenewing(anyString());
        verifyNoInteractions(summonerCollectionPublishPort);
    }

    @DisplayName("마지막 Riot 호출 뒤 2분이 안 지났으면 FAILED (쿨다운은 남는다)")
    @Test
    void requestRenewal_riotCalledWithinTwoMinutes_returnsFailed() {
        // given
        given(summonerQueryPort.findSummoner(PUUID))
                .willReturn(Optional.of(summoner("KR", LocalDateTime.now().minusSeconds(90))));
        given(summonerRenewalStatePort.tryStartClickCooldown(PUUID)).willReturn(true);

        // when
        SummonerRenewalReadModel result = service.requestRenewal(PUUID);

        // then
        assertThat(result.status()).isEqualTo("FAILED");
        verify(summonerRenewalStatePort, never()).markRenewing(anyString());
        verifyNoInteractions(summonerCollectionPublishPort);
    }

    @DisplayName("갱신할 수 있으면 진행 표시 후 플랫폼 이름(NA1 → NA)으로 발행하고 SUCCESS")
    @Test
    void requestRenewal_renewable_marksAndPublishesWithPlatformName() {
        // given
        given(summonerQueryPort.findSummoner(PUUID))
                .willReturn(Optional.of(summoner("NA1", LocalDateTime.now().minusMinutes(3))));
        given(summonerRenewalStatePort.tryStartClickCooldown(PUUID)).willReturn(true);

        // when
        SummonerRenewalReadModel result = service.requestRenewal(PUUID);

        // then
        assertThat(result).isEqualTo(new SummonerRenewalReadModel(PUUID, "SUCCESS"));
        assertThat(result.rejected()).isFalse();
        verify(summonerRenewalStatePort).markRenewing(PUUID);
        verify(summonerCollectionPublishPort).publishForRenewal(List.of(PUUID), "NA");
    }

    @DisplayName("Riot 을 부른 적 없는 소환사는 바로 갱신할 수 있다")
    @Test
    void requestRenewal_neverCalledRiot_publishes() {
        // given
        given(summonerQueryPort.findSummoner(PUUID)).willReturn(Optional.of(summoner("KR", null)));
        given(summonerRenewalStatePort.tryStartClickCooldown(PUUID)).willReturn(true);

        // when
        service.requestRenewal(PUUID);

        // then
        verify(summonerCollectionPublishPort).publishForRenewal(any(), any());
    }

    @DisplayName("상태는 진행 표시가 있으면 PROGRESS, 없으면 SUCCESS — 저장소를 보지 않는다")
    @Test
    void getRenewalStatus_byMarker_returnsProgressOrSuccess() {
        // given
        given(summonerRenewalStatePort.isRenewing("renewing")).willReturn(true);
        given(summonerRenewalStatePort.isRenewing("idle")).willReturn(false);

        // when & then
        assertThat(service.getRenewalStatus("renewing").status()).isEqualTo("PROGRESS");
        assertThat(service.getRenewalStatus("idle").status()).isEqualTo("SUCCESS");
        verifyNoInteractions(summonerQueryPort);
    }

    private Summoner summoner(String platformId, LocalDateTime lastRiotCallDate) {
        return Summoner.builder()
                .puuid(PUUID)
                .gameIdentity(new GameIdentity("hide on bush", "KR1"))
                .platformId(platformId)
                .statusInfo(new StatusInfo(6, 700))
                .revisionInfo(new RevisionInfo(LocalDateTime.of(2026, 3, 1, 0, 0), lastRiotCallDate))
                .leagueInfos(Set.of())
                .build();
    }
}
