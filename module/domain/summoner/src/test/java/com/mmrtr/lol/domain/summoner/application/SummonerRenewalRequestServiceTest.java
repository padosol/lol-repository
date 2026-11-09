package com.mmrtr.lol.domain.summoner.application;

import com.mmrtr.lol.domain.league.application.port.SummonerCollectionPublishPort;
import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;
import com.mmrtr.lol.domain.summoner.application.port.SummonerQueryPort;
import com.mmrtr.lol.domain.summoner.application.port.SummonerRenewalStatePort;
import com.mmrtr.lol.domain.summoner.domain.RenewalRecord;
import com.mmrtr.lol.domain.summoner.domain.RenewalStatus;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SummonerRenewalRequestServiceTest {

    private static final String PUUID = "puuid-1";
    private static final long FUTURE = Long.MAX_VALUE;

    @Mock
    private SummonerQueryPort summonerQueryPort;

    @Mock
    private SummonerRenewalStatePort summonerRenewalStatePort;

    @Mock
    private SummonerCollectionPublishPort summonerCollectionPublishPort;

    @Mock
    private RenewalRecordPort renewalRecordPort;

    @Captor
    private ArgumentCaptor<String> idCaptor;

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
        verifyNoInteractions(summonerRenewalStatePort, renewalRecordPort, summonerCollectionPublishPort);
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
        verifyNoInteractions(summonerRenewalStatePort, renewalRecordPort, summonerCollectionPublishPort);
    }

    @DisplayName("갱신 레코드가 기한 전 PROGRESS 면 SUCCESS — 쿨다운도 발행도 하지 않는다")
    @Test
    void requestRenewal_recordProgress_returnsSuccessWithoutPublishing() {
        // given
        given(summonerQueryPort.findSummoner(PUUID)).willReturn(Optional.of(summoner("KR", null)));
        given(renewalRecordPort.find(PUUID)).willReturn(Optional.of(record(RenewalStatus.PROGRESS, FUTURE)));

        // when
        SummonerRenewalReadModel result = service.requestRenewal(PUUID);

        // then
        assertThat(result.status()).isEqualTo("SUCCESS");
        verify(summonerRenewalStatePort, never()).tryStartClickCooldown(anyString());
        verify(renewalRecordPort, never()).open(anyString(), anyString(), anyString());
        verifyNoInteractions(summonerCollectionPublishPort);
    }

    @DisplayName("레코드가 FAILED 면 진행 중이 아니라 새 갱신을 받는다")
    @Test
    void requestRenewal_recordFailed_acceptsNewRenewal() {
        // given
        given(summonerQueryPort.findSummoner(PUUID)).willReturn(Optional.of(summoner("KR", null)));
        given(renewalRecordPort.find(PUUID)).willReturn(Optional.of(record(RenewalStatus.FAILED, FUTURE)));
        given(summonerRenewalStatePort.tryStartClickCooldown(PUUID)).willReturn(true);

        // when
        SummonerRenewalReadModel result = service.requestRenewal(PUUID);

        // then
        assertThat(result.status()).isEqualTo("SUCCESS");
        verify(renewalRecordPort).open(eq(PUUID), anyString(), eq("KR"));
    }

    @DisplayName("기한이 지난 PROGRESS 레코드도 진행 중이 아니라 새 갱신을 받는다")
    @Test
    void requestRenewal_recordProgressPastDeadline_acceptsNewRenewal() {
        // given
        given(summonerQueryPort.findSummoner(PUUID)).willReturn(Optional.of(summoner("KR", null)));
        given(renewalRecordPort.find(PUUID)).willReturn(Optional.of(record(RenewalStatus.PROGRESS, 1L)));
        given(summonerRenewalStatePort.tryStartClickCooldown(PUUID)).willReturn(true);

        // when
        service.requestRenewal(PUUID);

        // then
        verify(renewalRecordPort).open(eq(PUUID), anyString(), eq("KR"));
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
        verify(renewalRecordPort, never()).open(anyString(), anyString(), anyString());
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
        verify(renewalRecordPort, never()).open(anyString(), anyString(), anyString());
        verifyNoInteractions(summonerCollectionPublishPort);
    }

    @DisplayName("접수하면 레코드를 연 뒤 플랫폼 이름(NA1 → NA)과 renewalId 로 발행한다")
    @Test
    void requestRenewal_renewable_opensRecordAndPublishesWithRenewalId() {
        // given
        given(summonerQueryPort.findSummoner(PUUID))
                .willReturn(Optional.of(summoner("NA1", LocalDateTime.now().minusMinutes(3))));
        given(summonerRenewalStatePort.tryStartClickCooldown(PUUID)).willReturn(true);

        // when
        SummonerRenewalReadModel result = service.requestRenewal(PUUID);

        // then
        InOrder order = inOrder(renewalRecordPort, summonerCollectionPublishPort);
        order.verify(renewalRecordPort).open(eq(PUUID), idCaptor.capture(), eq("NA"));
        order.verify(summonerCollectionPublishPort).publishForRenewal(PUUID, "NA", idCaptor.getValue());
        assertThat(idCaptor.getValue()).isNotBlank();
        assertThat(result).isEqualTo(new SummonerRenewalReadModel(PUUID, "SUCCESS", null));
        assertThat(result.rejected()).isFalse();
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
        verify(summonerCollectionPublishPort).publishForRenewal(eq(PUUID), eq("KR"), anyString());
    }

    @DisplayName("상태는 레코드가 기한 전 PROGRESS 면 PROGRESS, 저장된 SUCCESS 는 SUCCESS")
    @Test
    void getRenewalStatus_byRecord_returnsStoredStatus() {
        // given
        given(renewalRecordPort.find("renewing")).willReturn(Optional.of(record(RenewalStatus.PROGRESS, FUTURE)));
        given(renewalRecordPort.find("done")).willReturn(Optional.of(record(RenewalStatus.SUCCESS, FUTURE)));

        // when & then
        assertThat(service.getRenewalStatus("renewing").status()).isEqualTo("PROGRESS");
        assertThat(service.getRenewalStatus("done")).isEqualTo(new SummonerRenewalReadModel("done", "SUCCESS", null));
        verifyNoInteractions(summonerQueryPort);
    }

    @DisplayName("기한이 지난 PROGRESS 는 FAILED 와 안내 문구를 돌려준다")
    @Test
    void getRenewalStatus_progressPastDeadline_returnsFailedWithMessage() {
        // given
        given(renewalRecordPort.find(PUUID))
                .willReturn(Optional.of(record(RenewalStatus.PROGRESS, System.currentTimeMillis() - 1)));

        // when
        SummonerRenewalReadModel result = service.getRenewalStatus(PUUID);

        // then
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.message()).isEqualTo("잠시 후 다시 시도해 주세요");
    }

    @DisplayName("저장된 FAILED 레코드도 안내 문구와 함께 FAILED")
    @Test
    void getRenewalStatus_storedFailed_returnsFailedWithMessage() {
        // given
        given(renewalRecordPort.find(PUUID)).willReturn(Optional.of(record(RenewalStatus.FAILED, FUTURE)));

        // when
        SummonerRenewalReadModel result = service.getRenewalStatus(PUUID);

        // then
        assertThat(result).isEqualTo(SummonerRenewalReadModel.retryLater(PUUID));
    }

    @DisplayName("레코드가 없으면 SUCCESS (message 없음)")
    @Test
    void getRenewalStatus_noRecord_returnsSuccess() {
        // given
        given(renewalRecordPort.find(PUUID)).willReturn(Optional.empty());

        // when
        SummonerRenewalReadModel result = service.getRenewalStatus(PUUID);

        // then
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.message()).isNull();
    }

    private RenewalRecord record(RenewalStatus status, long deadlineAt) {
        return new RenewalRecord("r1", status, null, 0, null, 0L, deadlineAt, null, "KR");
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
