package com.mmrtr.lol.support.error;

import lombok.Getter;

@Getter
public class CoreException extends RuntimeException{
    private final ErrorType errorType;
    private final String message;

    public CoreException(ErrorType errorType, String message) {
        this.errorType = errorType;
        this.message = message;
    }
}
