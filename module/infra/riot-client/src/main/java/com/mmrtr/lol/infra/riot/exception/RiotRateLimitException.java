package com.mmrtr.lol.infra.riot.exception;

import lombok.Getter;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatusCode;

import java.time.Duration;

@Getter
public class RiotRateLimitException extends RiotClientException {

    private final Duration retryAfter;

    public RiotRateLimitException(Duration retryAfter, HttpStatusCode status, String errorBody, LogLevel logLevel) {
        super(status, errorBody, logLevel);
        this.retryAfter = retryAfter;
    }
}
