package com.mmrtr.lol.riot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "riot")
public class RiotAPIProperties {
    private String apiKey;
    private int timeout = 3; // 타임아웃 (초)
    private int retryAttempts = 2; // 재시도 횟수
    private int retryDelay = 2; // 재시도 간격 (초)
}
