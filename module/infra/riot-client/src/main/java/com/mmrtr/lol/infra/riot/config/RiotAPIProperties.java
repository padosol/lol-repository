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
    private Mock mock = new Mock();

    /** 가짜 Riot 서버 ({@code riot.mock.*}). lab 프로파일 전용. */
    @Getter
    @Setter
    public static class Mock {
        /** 비어 있으면 실제 Riot 으로 보낸다. */
        private String baseUrl;
    }

    /** Riot 호출 한도 관리 설정 ({@code riot.rate-limit.*}). */
    @Getter
    @Setter
    public static class RateLimit {
        /** 응답 헤더로 배우기 전 app 한도. */
        private String appDefault = "500:10";
        /** 규칙(버킷 × 창)의 상한 = max(1, floor(한도 × 이 비율)). */
        private double safetyRatio = 0.9;
        /** 응답 Count 헤더가 한도의 이 비율 이상이면 버킷을 잠시 멈춘다. */
        private double countPauseRatio = 0.95;
        /** 우선순위별 누적 대기 상한. 넘으면 보내지 않고 포기한다. */
        private Duration userMaxWait = Duration.ofSeconds(10);
        private Duration deferredMaxWait = Duration.ofSeconds(60);
        private Duration backgroundMaxWait = Duration.ofSeconds(30);
        /** 마지막 USER 요청 뒤 이만큼은 그 routing 을 유저 활동 중으로 본다. */
        private Duration userActiveWindow = Duration.ofSeconds(10);
        /** 유저 활동 중 DEFERRED·BACKGROUND 가 쓸 수 있는 app·method 규칙 상한의 비율. */
        private double deferredCeilingRatio = 0.8;
        private double backgroundCeilingRatio = 0.5;
        /** BACKGROUND 전용 1초 창 규칙의 상한(안전율 곱하지 않음). 0 이하면 규칙을 두지 않는다. */
        private int backgroundMaxPerSecond = 40;
        /** 다른 인스턴스가 배운 한도를 Redis 에서 다시 읽는 주기. */
        private Duration limitsRefresh = Duration.ofSeconds(10);
        /** 429 에 Retry-After 가 없을 때 정지 시간. */
        private Duration defaultPause = Duration.ofSeconds(1);
    }
}
