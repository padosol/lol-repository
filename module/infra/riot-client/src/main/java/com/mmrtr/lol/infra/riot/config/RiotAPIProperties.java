package com.mmrtr.lol.infra.riot.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
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
        /** Riot app 한도 ("500:10,30000:600" 형식). routing 마다 따로 센다. */
        private String appLimits = "500:10";
        /** method 이름({@code RiotMethod.methodName()}) → 한도. 없는 method 는 app 한도만 본다. */
        private Map<String, String> methodLimits = new LinkedHashMap<>();
        /** 규칙(버킷 × 창)의 상한 = max(1, floor(한도 × 이 비율)). */
        private double safetyRatio = 0.9;
        /** 마지막 USER 요청 뒤 이만큼은 그 routing 을 유저 활동 중으로 본다. */
        private Duration userActiveWindow = Duration.ofSeconds(10);
        /** 유저 활동 중 DEFERRED·BACKGROUND 가 쓸 수 있는 app·method 규칙 상한의 비율. */
        private double deferredCeilingRatio = 0.8;
        private double backgroundCeilingRatio = 0.5;
        /** BACKGROUND 전용 1초 창 규칙의 상한(안전율 곱하지 않음). 0 이하면 규칙을 두지 않는다. */
        private int backgroundMaxPerSecond = 40;
        /** 429 에 Retry-After 가 없을 때 정지 시간. */
        private Duration defaultPause = Duration.ofSeconds(1);
    }
}
