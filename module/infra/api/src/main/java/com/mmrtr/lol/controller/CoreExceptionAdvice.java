package com.mmrtr.lol.controller;

import com.mmrtr.lol.controller.support.response.ApiResponse;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorMessage;
import com.mmrtr.lol.support.error.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class CoreExceptionAdvice {

    @ExceptionHandler
    public ResponseEntity<ErrorMessage> coreException(CoreException e) {

        switch (e.getErrorType().getLogLevel()) {
            case ERROR -> log.error("CoreException : {}", e.getMessage());
            case INFO -> log.warn("CoreException : {}", e.getMessage());
            default -> log.info("CoreException : {}", e.getMessage());
        }

        return ResponseEntity
                .status(e.getErrorType().getHttpStatus())
                .body(new ErrorMessage());
    }

    /** 허용 prefix 안이지만 매핑이 없는 경로. 핸들러가 없어 {@code PublicApiExceptionAdvice} 가 적용되지 않으므로 여기서 받는다. */
    @ExceptionHandler
    public ResponseEntity<ApiResponse<ErrorMessage>> noResource(NoResourceFoundException e) {
        return ResponseEntity
                .status(ErrorType.NOT_FOUND_API.getHttpStatus())
                .body(ApiResponse.error(ErrorType.NOT_FOUND_API));
    }

}
