package com.mmrtr.lol.infra.riot.exception;

import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatusCode;

public class RiotClientNotFoundException extends RuntimeException {

    private final HttpStatusCode status;

    public RiotClientNotFoundException(HttpStatusCode status, String errorBody, LogLevel logLevel) {
        super(errorBody);
        this.status = status;
    }
}
