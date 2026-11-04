package com.mmrtr.lol.domain.summoner.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RenewalRecordTest {

    private static RenewalRecord record(RenewalStatus status, String reason, int steps, long deadlineAt) {
        return new RenewalRecord("r1", status, reason, steps, false, 0L, deadlineAt, null, "KR");
    }

    @Test
    @DisplayName("진행 중인데 기한이 지났으면 FAILED 로 본다")
    void statusAt_progressPastDeadline_failed() {
        // given
        RenewalRecord record = record(RenewalStatus.PROGRESS, null, 0, 1000);

        // when & then
        assertThat(record.statusAt(1001)).isEqualTo(RenewalStatus.FAILED);
        assertThat(record.statusAt(1000)).isEqualTo(RenewalStatus.PROGRESS);
    }

    @Test
    @DisplayName("끝난 상태는 기한보다 우선한다")
    void statusAt_terminalStatus_ignoresDeadline() {
        assertThat(record(RenewalStatus.SUCCESS, null, 15, 1000).statusAt(5000)).isEqualTo(RenewalStatus.SUCCESS);
        assertThat(record(RenewalStatus.FAILED, "x", 0, 1000).statusAt(500)).isEqualTo(RenewalStatus.FAILED);
    }

    @Test
    @DisplayName("renewalId 와 단계 비트를 확인한다")
    void isForAndHas() {
        // given
        RenewalRecord record = record(RenewalStatus.PROGRESS, null, 1 | 4, 1000);

        // when & then
        assertThat(record.isFor("r1")).isTrue();
        assertThat(record.isFor("r2")).isFalse();
        assertThat(record.has(RenewalStep.ACCOUNT)).isTrue();
        assertThat(record.has(RenewalStep.SUMMONER)).isFalse();
        assertThat(record.has(RenewalStep.LEAGUE)).isTrue();
        assertThat(record.has(RenewalStep.MATCH)).isFalse();
    }

    @Test
    @DisplayName("기한 실패는 FAILED 이면서 reason 이 deadline 인 경우뿐이다")
    void isDeadlineFailure() {
        assertThat(record(RenewalStatus.FAILED, "deadline", 0, 1).isDeadlineFailure()).isTrue();
        assertThat(record(RenewalStatus.FAILED, "not_found", 0, 1).isDeadlineFailure()).isFalse();
        assertThat(record(RenewalStatus.PROGRESS, "deadline", 0, 1).isDeadlineFailure()).isFalse();
    }

    @Test
    @DisplayName("모든 단계 비트의 합은 15 다")
    void allBits() {
        int sum = 0;
        for (RenewalStep step : RenewalStep.values()) {
            sum |= step.bit();
        }
        assertThat(sum).isEqualTo(RenewalStep.ALL_BITS).isEqualTo(15);
    }
}
