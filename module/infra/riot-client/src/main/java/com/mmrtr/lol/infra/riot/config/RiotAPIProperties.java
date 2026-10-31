package com.mmrtr.lol.infra.riot.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@link RiotApiConfig} 의 {@code @EnableConfigurationProperties} 로만 등록한다. */
@Getter
@Setter
@ConfigurationProperties(prefix = "riot")
public class RiotAPIProperties {
    private String apiKey;
    private int timeout = 3;
    private int readTimeout = 5;
    private int retryAttempts = 2;
    private int retryDelay = 2;
    private RateLimit rateLimit = new RateLimit();

    /** Riot 호출 한도 관리 설정 ({@code riot.rate-limit.*}). */
    @Getter
    @Setter
    public static class RateLimit {
        /** 응답 헤더로 배우기 전 app 한도. */
        private String appDefault = "500:10";
        private double safetyRatio = 0.9;
        /** USER 가 이번 초가 찼을 때 예약할 수 있는 최대 초 수. */
        private int lookaheadSeconds = 10;
        /** DEFERRED·BACKGROUND 가 USER 몫으로 남겨 두는 칸 비율. */
        private double reserveRatio = 0.2;
        /** 응답 Count 헤더가 한도의 이 비율 이상이면 버킷을 잠시 멈춘다. */
        private double countPauseRatio = 0.95;
        private Duration deferredMaxWait = Duration.ofSeconds(60);
        private Duration backgroundMaxWait = Duration.ofSeconds(30);
        private Duration yieldBackoff = Duration.ofMillis(200);
        /** 429 에 Retry-After 가 없을 때 정지 시간. */
        private Duration defaultPause = Duration.ofSeconds(1);
    }
}
