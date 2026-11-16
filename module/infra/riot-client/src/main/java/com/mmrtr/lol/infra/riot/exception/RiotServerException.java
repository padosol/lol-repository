package com.mmrtr.lol.infra.riot.exception;

import lombok.Getter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

@Getter
public class RiotServerException extends RuntimeException {
    private final HttpStatusCode status;
    private final String errorBody;
    /** 응답 헤더(한도 학습용). */
    private final HttpHeaders headers;

    public RiotServerException(HttpStatusCode status, String errorBody) {
        this(status, errorBody, HttpHeaders.EMPTY);
    }

    public RiotServerException(HttpStatusCode status, String errorBody, HttpHeaders headers) {
        super(String.format("Riot API server error: %s, Body: %s", status, errorBody));
        this.status = status;
        this.errorBody = errorBody;
        this.headers = headers != null ? headers : HttpHeaders.EMPTY;
    }
}
