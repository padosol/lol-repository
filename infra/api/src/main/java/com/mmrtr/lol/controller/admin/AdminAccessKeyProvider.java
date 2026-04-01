package com.mmrtr.lol.controller.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class AdminAccessKeyProvider {

    private final String accessKey;

    public AdminAccessKeyProvider() {
        this.accessKey = UUID.randomUUID().toString();
        log.info("[Admin Access Key] {}", accessKey);
    }

    public boolean validate(String key) {
        return accessKey.equals(key);
    }
}
