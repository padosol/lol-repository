package com.mmrtr.lol.infra.riot.config;

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
    private int timeout = 3;
    private int retryAttempts = 2;
    private int retryDelay = 2;
}
