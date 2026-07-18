package com.mmrtr.lol.config;

import java.util.TimeZone;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Configuration;

/**
 * 애플리케이션 기본 타임존을 KST(Asia/Seoul)로 고정한다.
 *
 * bootRun / IDE Run / 컨테이너 / 테스트 등 실행 방식과 무관하게 JVM 기본 타임존을
 * 동일하게 맞춰, LocalDateTime.now()·new Date()·로그 타임스탬프·@Scheduled 등이
 * 모든 환경에서 서울 시각을 따르도록 한다.
 */
@Configuration
public class TimeZoneConfig {

    private static final String DEFAULT_TIME_ZONE = "Asia/Seoul";

    @PostConstruct
    void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(DEFAULT_TIME_ZONE));
    }
}
