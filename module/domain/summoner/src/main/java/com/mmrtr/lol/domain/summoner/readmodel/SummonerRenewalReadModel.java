package com.mmrtr.lol.domain.summoner.readmodel;

import com.mmrtr.lol.domain.summoner.domain.RenewalStatus;

/** 갱신 요청·상태 응답. 필드가 lol-server {@code SummonerRenewalResponse} 와 같다. */
public record SummonerRenewalReadModel(String puuid, String status) {

    public static SummonerRenewalReadModel of(String puuid, RenewalStatus status) {
        return new SummonerRenewalReadModel(puuid, status.name());
    }

    /** 갱신을 시작하지 않았으면 클라이언트가 상태를 폴링하지 않는다 (POST 는 200, 아니면 202). */
    public boolean rejected() {
        return RenewalStatus.FAILED.name().equals(status);
    }
}
