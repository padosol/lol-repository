package com.mmrtr.lol.domain.summoner.domain;

import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;

/** 갱신 하나의 상태 기록. 상태 전이의 단일 진실원천. */
public record RenewalRecord(
        String renewalId,
        RenewalStatus status,
        String reason,
        int steps,
        long acceptedAt,
        long deadlineAt,
        Long riotRevisionDate,
        String platform) {

    public boolean isFor(String id) {
        return renewalId != null && renewalId.equals(id);
    }

    public boolean has(RenewalStep step) {
        return (steps & step.bit()) != 0;
    }

    /** 진행 중인데 기한이 지났으면 FAILED, 그 밖에는 저장된 상태. */
    public RenewalStatus statusAt(long nowMillis) {
        if (status == RenewalStatus.PROGRESS && nowMillis > deadlineAt) {
            return RenewalStatus.FAILED;
        }
        return status;
    }

    public boolean isDeadlineFailure() {
        return status == RenewalStatus.FAILED && RenewalRecordPort.DEADLINE_REASON.equals(reason);
    }
}
