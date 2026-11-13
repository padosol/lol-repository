package com.mmrtr.lol.infra.rabbitmq.renewal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;
import com.mmrtr.lol.domain.summoner.domain.RenewalRecord;
import com.mmrtr.lol.domain.summoner.domain.RenewalStatus;
import com.mmrtr.lol.domain.summoner.domain.RenewalTransition;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RenewalCompletionJudgeTest {

    private static final long ACCEPTED = 1_000_000L;

    private final RenewalRecordPort recordPort = mock(RenewalRecordPort.class);
    private final RenewalFinisher finisher = mock(RenewalFinisher.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RenewalCompletionJudge judge = new RenewalCompletionJudge(recordPort, finisher, registry);
    private final List<String> activePuuids = new ArrayList<>();

    private RenewalRecord record(String id, RenewalStatus status, String reason) {
        return new RenewalRecord(id, status, reason, 7, null, ACCEPTED, ACCEPTED + 5000, null, "KR");
    }

    private void active(String puuid, RenewalRecord record) {
        activePuuids.add(puuid);
        given(recordPort.findActive()).willReturn(List.copyOf(activePuuids));
        given(recordPort.find(puuid)).willReturn(Optional.ofNullable(record));
    }

    @Test
    @DisplayName("진행 중인 갱신은 failIfOverdue 로 기한을 본다")
    void sweep_progressPastDeadline_callsFailIfOverdue() {
        // given
        active("p1", record("r1", RenewalStatus.PROGRESS, null));
        given(recordPort.failIfOverdue("p1", "r1")).willReturn(RenewalTransition.FAILED);

        // when
        judge.sweep();

        // then
        verify(recordPort, never()).markSteps(any(), any(), any());
        verify(finisher).finish("p1", RenewalTransition.FAILED);
    }

    @Test
    @DisplayName("기한 실패 레코드는 건드리지 않는다. 늦은 완료는 단계 비트가 닫는다")
    void sweep_deadlineFailedRecord_untouched() {
        // given
        active("p1", record("r1", RenewalStatus.FAILED, RenewalRecordPort.DEADLINE_REASON));

        // when
        judge.sweep();

        // then
        verify(recordPort, never()).failIfOverdue(anyString(), anyString());
        verify(recordPort, never()).markSteps(any(), any(), any());
        verify(recordPort, never()).deactivate(anyString());
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
    @DisplayName("한 갱신이 예외를 던져도 나머지는 계속 처리하고 active 게이지를 남긴다")
    void sweep_oneRecordThrows_othersContinue() {
        // given
        active("p1", record("r1", RenewalStatus.PROGRESS, null));
        active("p2", record("r2", RenewalStatus.PROGRESS, null));
        given(recordPort.failIfOverdue("p1", "r1")).willThrow(new IllegalStateException("boom"));
        given(recordPort.failIfOverdue("p2", "r2")).willReturn(RenewalTransition.FAILED);

        // when
        judge.sweep();

        // then
        verify(finisher).finish("p2", RenewalTransition.FAILED);
        assertThat(registry.get("lol.renewal.active").gauge().value()).isEqualTo(2.0);
    }
}
