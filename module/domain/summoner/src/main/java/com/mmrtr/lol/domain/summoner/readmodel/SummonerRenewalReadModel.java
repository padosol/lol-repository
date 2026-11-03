package com.mmrtr.lol.domain.summoner.readmodel;

import com.mmrtr.lol.domain.summoner.domain.RenewalStatus;

/** 갱신 요청·상태 응답. {@code message} 는 FAILED 상태 조회에서만 채워 화면에 보일 문구를 준다. */
public record SummonerRenewalReadModel(String puuid, String status, String message) {

    public static final String RETRY_LATER_MESSAGE = "잠시 후 다시 시도해 주세요";

    public static SummonerRenewalReadModel of(String puuid, RenewalStatus status) {
        return new SummonerRenewalReadModel(puuid, status.name(), null);
    }

    public static SummonerRenewalReadModel retryLater(String puuid) {
        return new SummonerRenewalReadModel(puuid, RenewalStatus.FAILED.name(), RETRY_LATER_MESSAGE);
    }

    /** 갱신을 시작하지 않았으면 클라이언트가 상태를 폴링하지 않는다 (POST 는 200, 아니면 202). */
    public boolean rejected() {
        return RenewalStatus.FAILED.name().equals(status);
    }
}
