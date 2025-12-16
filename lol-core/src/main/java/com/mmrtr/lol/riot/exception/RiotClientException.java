package com.mmrtr.lol.riot.exception;

import lombok.Getter;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatusCode;

@Getter
public class RiotClientException extends RuntimeException {
    private final HttpStatusCode status;
    private final String errorBody;
    private final LogLevel logLevel;

    public RiotClientException(HttpStatusCode status, String errorBody, LogLevel logLevel) {
        super(errorBody);
        this.status = status;
        this.errorBody = errorBody;
        this.logLevel = logLevel;
    }
}
