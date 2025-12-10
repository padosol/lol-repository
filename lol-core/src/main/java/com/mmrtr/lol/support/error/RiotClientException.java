package com.mmrtr.lol.support.error;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class RiotClientException extends RuntimeException {
    private final HttpStatusCode status;
    private final String errorBody;

    public RiotClientException(HttpStatusCode status, String errorBody) {
        super(String.format("Riot API client error: %s, Body: %s", status, errorBody));
        this.status = status;
        this.errorBody = errorBody;
    }
}
