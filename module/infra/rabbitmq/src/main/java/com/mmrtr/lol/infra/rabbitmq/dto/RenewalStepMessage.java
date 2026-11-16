package com.mmrtr.lol.infra.rabbitmq.dto;

/**
 * 갱신 단계(계정·리그·매치 큐) 공통 메시지. platform 은 이름(KR, NA)이다.
 */
public record RenewalStepMessage(
        String renewalId,
        String puuid,
        String platform,
        long startTime,
        int attempt
) {

    public RenewalStepMessage nextAttempt() {
        return new RenewalStepMessage(renewalId, puuid, platform, startTime, attempt + 1);
    }
}
