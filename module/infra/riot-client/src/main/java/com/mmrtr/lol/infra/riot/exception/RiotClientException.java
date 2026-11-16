package com.mmrtr.lol.infra.riot.exception;

import lombok.Getter;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

@Getter
public class RiotClientException extends RuntimeException {
    private final HttpStatusCode status;
    private final String errorBody;
    private final LogLevel logLevel;
    /** 응답 헤더(한도 학습용). 응답이 없었으면 비어 있다. */
    private final HttpHeaders headers;

    public RiotClientException(HttpStatusCode status, String errorBody, LogLevel logLevel) {
        this(status, errorBody, logLevel, HttpHeaders.EMPTY);
    }

    public RiotClientException(HttpStatusCode status, String errorBody, LogLevel logLevel, HttpHeaders headers) {
        super(errorBody);
        this.status = status;
        this.errorBody = errorBody;
        this.logLevel = logLevel;
        this.headers = headers != null ? headers : HttpHeaders.EMPTY;
    }
}
