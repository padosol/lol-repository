package com.mmrtr.lol.infra.rabbitmq.renewal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mmrtr.lol.domain.match.application.port.MatchRepositoryPort;
import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;
import com.mmrtr.lol.domain.summoner.domain.RenewalRecord;
import com.mmrtr.lol.domain.summoner.domain.RenewalStatus;
import com.mmrtr.lol.domain.summoner.domain.RenewalStep;
import com.mmrtr.lol.domain.summoner.domain.RenewalTransition;
import com.mmrtr.lol.infra.redis.service.RenewalMatchClaimService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RenewalCompletionJudgeTest {

    private static final long ACCEPTED = 1_000_000L;

    private final RenewalRecordPort recordPort = mock(RenewalRecordPort.class);
    private final MatchRepositoryPort matchRepositoryPort = mock(MatchRepositoryPort.class);
    private final RenewalMatchClaimService claimService = mock(RenewalMatchClaimService.class);
    private final RenewalFinisher finisher = mock(RenewalFinisher.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RenewalCompletionJudge judge =
            new RenewalCompletionJudge(recordPort, matchRepositoryPort, claimService, finisher, registry);
    private final List<String> activePuuids = new ArrayList<>();

    private RenewalRecord record(String id, RenewalStatus status, String reason, int steps, boolean ready) {
        return new RenewalRecord(id, status, reason, steps, ready ? Set.of() : null,
                ACCEPTED, ACCEPTED + 5000, null, "KR");
    }

    private void active(String puuid, RenewalRecord record, String... targets) {
        activePuuids.add(puuid);
        RenewalRecord withTargets = record == null || !record.targetsReady() ? record : new RenewalRecord(
                record.renewalId(), record.status(), record.reason(), record.steps(), Set.of(targets),
                record.acceptedAt(), record.deadlineAt(), record.riotRevisionDate(), record.platform());
        given(recordPort.findActive()).willReturn(List.copyOf(activePuuids));
        given(recordPort.find(puuid)).willReturn(Optional.ofNullable(withTargets));
    }

    @Test
    @DisplayName("대상이 전부 DB 에 있으면 매치 비트를 켜고 끝낸다")
    void sweep_allTargetsInDb_marksMatchStep() {
        // given
        active("p1", record("r1", RenewalStatus.PROGRESS, null, 7, true), "m1", "m2");
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(List.of("m1", "m2"));
        given(recordPort.markSteps("p1", "r1", EnumSet.of(RenewalStep.MATCH)))
                .willReturn(RenewalTransition.SUCCEEDED);

        // when
        judge.sweep();

        // then
        verify(recordPort).markSteps("p1", "r1", EnumSet.of(RenewalStep.MATCH));
        verify(finisher).finish("p1", RenewalTransition.SUCCEEDED);
    }

    @Test
    @DisplayName("대상을 다 기록하기 전에는 매치 비트를 켜지 않는다")
    void sweep_targetsNotReady_doesNotMarkMatch() {
        // given
        active("p1", record("r1", RenewalStatus.PROGRESS, null, 2, false));

        // when
        judge.sweep();

        // then
        verify(recordPort, never()).markSteps(any(), any(), any());
        verify(matchRepositoryPort, never()).findExistingMatchIds(any());
    }

    @Test
    @DisplayName("대상이 덜 저장됐으면 failIfOverdue 로 기한을 본다")
    void sweep_progressPastDeadline_callsFailIfOverdue() {
        // given
        active("p1", record("r1", RenewalStatus.PROGRESS, null, 7, true), "m1", "m2");
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(List.of("m1"));
        given(recordPort.failIfOverdue("p1", "r1")).willReturn(RenewalTransition.FAILED);

        // when
        judge.sweep();

        // then
        verify(recordPort, never()).markSteps(any(), any(), any());
        verify(finisher).finish("p1", RenewalTransition.FAILED);
    }

    @Test
    @DisplayName("접수 뒤에 남은 실패 표시가 있으면 match_failed 로 끝낸다")
    void sweep_failedMarkAfterAccepted_failsMatchFailed() {
        // given
        active("p1", record("r1", RenewalStatus.PROGRESS, null, 7, true), "m1", "m2");
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(List.of());
        given(claimService.findFailedAt(any())).willReturn(Map.of("m1", ACCEPTED + 10));
        given(recordPort.fail("p1", "r1", "match_failed")).willReturn(RenewalTransition.FAILED);

        // when
        judge.sweep();

        // then
        verify(recordPort).fail("p1", "r1", "match_failed");
        verify(finisher).finish("p1", RenewalTransition.FAILED);
        verify(recordPort, never()).failIfOverdue(any(), any());
    }

    @Test
    @DisplayName("지난 갱신의 실패 표시는 무시한다")
    void sweep_failedMarkBeforeAccepted_ignored() {
        // given
        active("p1", record("r1", RenewalStatus.PROGRESS, null, 7, true), "m1", "m2");
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(List.of());
        given(claimService.findFailedAt(any())).willReturn(Map.of("m1", ACCEPTED - 10));

        // when
        judge.sweep();

        // then
        verify(recordPort, never()).fail(any(), any(), any());
    }

    @Test
    @DisplayName("기한 FAILED 갱신도 대상이 뒤늦게 저장되면 늦은 완료로 끝낸다")
    void sweep_deadlineFailedRecord_lateCompletesWhenTargetsLand() {
        // given
        active("p1", record("r1", RenewalStatus.FAILED, "deadline", 7, true), "m1");
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(List.of("m1"));
        given(recordPort.markSteps("p1", "r1", EnumSet.of(RenewalStep.MATCH)))
                .willReturn(RenewalTransition.LATE_COMPLETED);

        // when
        judge.sweep();

        // then
        verify(finisher).finish("p1", RenewalTransition.LATE_COMPLETED);
        verify(recordPort, never()).fail(any(), any(), any());
        verify(recordPort, never()).failIfOverdue(any(), any());
    }

    @Test
    @DisplayName("레코드가 만료됐으면 활성 목록에서 뺀다")
    void sweep_recordExpired_deactivates() {
        // given
        active("p1", null);

        // when
        judge.sweep();

        // then
        verify(recordPort).deactivate("p1");
    }

    @Test
    @DisplayName("활성 갱신이 여럿이어도 DB 조회는 한 번만 한다")
    void sweep_queriesDbOnceForAllActive() {
        // given
        active("p1", record("r1", RenewalStatus.PROGRESS, null, 7, true), "m1");
        active("p2", record("r2", RenewalStatus.PROGRESS, null, 7, true), "m2");
        active("p3", record("r3", RenewalStatus.PROGRESS, null, 7, true), "m3");
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(List.of());

        // when
        judge.sweep();

        // then
        verify(matchRepositoryPort, times(1)).findExistingMatchIds(anyList());
    }

    @Test
    @DisplayName("한 갱신이 예외를 던져도 나머지는 계속 처리하고 active 게이지를 남긴다")
    void sweep_oneRecordThrows_othersContinue() {
        // given
        active("p1", record("r1", RenewalStatus.PROGRESS, null, 7, true), "m1");
        active("p2", record("r2", RenewalStatus.PROGRESS, null, 7, true), "m2");
        given(matchRepositoryPort.findExistingMatchIds(anyList())).willReturn(List.of("m1", "m2"));
        given(recordPort.markSteps("p1", "r1", EnumSet.of(RenewalStep.MATCH)))
                .willThrow(new IllegalStateException("boom"));
        given(recordPort.markSteps("p2", "r2", EnumSet.of(RenewalStep.MATCH)))
                .willReturn(RenewalTransition.SUCCEEDED);

        // when
        judge.sweep();

        // then
        verify(finisher).finish("p2", RenewalTransition.SUCCEEDED);
        assertThat(registry.get("lol.renewal.active").gauge().value()).isEqualTo(2.0);
    }
}
