package com.mmrtr.lol.infra.riot.exception;

import lombok.Getter;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

import java.time.Duration;

/** Riot 429. 다시 보내지 않는다. 호출 기록({@code RiotCallRecorder})이 이 값으로 버킷을 정지한다. */
@Getter
public class RiotRateLimitException extends RiotClientException {

    /** Retry-After(양의 정수 초). 없거나 잘못된 값이면 null. */
    private final Duration retryAfter;
    /** X-Rate-Limit-Type(application·method·service). 없으면 null. */
    private final String rateLimitType;

    public RiotRateLimitException(Duration retryAfter, String rateLimitType, HttpStatusCode status,
                                  String errorBody, LogLevel logLevel) {
        this(retryAfter, rateLimitType, status, errorBody, logLevel, HttpHeaders.EMPTY);
    }

    public RiotRateLimitException(Duration retryAfter, String rateLimitType, HttpStatusCode status,
                                  String errorBody, LogLevel logLevel, HttpHeaders headers) {
        super(status, errorBody, logLevel, headers);
        this.retryAfter = retryAfter;
        this.rateLimitType = rateLimitType;
    }
}
