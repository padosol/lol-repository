package com.mmrtr.lol.support.error;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class RiotServerException extends RuntimeException {
    private final HttpStatusCode status;
    private final String errorBody;

    public RiotServerException(HttpStatusCode status, String errorBody) {
        super(String.format("Riot API server error: %s, Body: %s", status, errorBody));
        this.status = status;
        this.errorBody = errorBody;
    }
}
