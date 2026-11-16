package com.mmrtr.lol.infra.riot.exception;

import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatusCode;

/**
 * Riot 404. {@link RiotClientException} 이라 어댑터의 {@code instanceof RiotClientException && status == 404}
 * 분기(없는 유저 → NOT_FOUND_USER, 게임 중 아님 → empty)가 이 예외를 받는다.
 */
public class RiotClientNotFoundException extends RiotClientException {

    public RiotClientNotFoundException(HttpStatusCode status, String errorBody, LogLevel logLevel) {
        super(status, errorBody, logLevel);
    }
}
