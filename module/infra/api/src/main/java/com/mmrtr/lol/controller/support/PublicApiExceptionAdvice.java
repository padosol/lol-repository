package com.mmrtr.lol.controller.support;

import com.mmrtr.lol.controller.support.response.ApiResponse;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorMessage;
import com.mmrtr.lol.support.error.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * {@link PublicApi} 컨트롤러 전용 예외 응답. lol-server {@code CoreExceptionAdvice} 와 같은 {@code ApiResponse} 에러 형식.
 * 레거시 {@code /api/riot/**} 응답은 lol-server 가 소비하므로 여기 적용하지 않는다.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = PublicApi.class)
public class PublicApiExceptionAdvice {

    @ExceptionHandler
    public ResponseEntity<ApiResponse<ErrorMessage>> coreException(CoreException e) {
        switch (e.getErrorType().getLogLevel()) {
            case ERROR -> log.error("CoreException : {}", e.getMessage());
            case WARN -> log.warn("CoreException : {}", e.getMessage());
            default -> log.info("CoreException : {}", e.getMessage());
        }
        return ResponseEntity
                .status(e.getErrorType().getHttpStatus())
                .body(ApiResponse.error(e.getErrorType()));
    }

    /** 필수 query 누락({@code platform} 등)·타입 불일치·검증 실패는 400 INVALID_INPUT. */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiResponse<ErrorMessage>> invalidInput(Exception e) {
        log.info("Invalid input : {}", e.getMessage());
        return ResponseEntity
                .status(ErrorType.INVALID_INPUT.getHttpStatus())
                .body(ApiResponse.error(ErrorType.INVALID_INPUT));
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<Object>> exception(Exception e) {
        log.error("Exception", e);
        return ResponseEntity
                .status(ErrorType.DEFAULT_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorType.DEFAULT_ERROR));
    }
}
