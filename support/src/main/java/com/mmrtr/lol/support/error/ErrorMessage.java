package com.mmrtr.lol.support.error;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class ErrorMessage {
    private String errorCode;
    private String message;
    private Timestamp timestamp;

    public ErrorMessage(ErrorType errorType) {
        this.errorCode = errorType.getErrorCode().name();
        this.message = errorType.getMessage();
        this.timestamp = Timestamp.valueOf(LocalDateTime.now());
    }
}
