package lol.mmrtr.lolrepository.controller;

import lol.mmrtr.lolrepository.support.error.CoreException;
import lol.mmrtr.lolrepository.support.error.ErrorMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

}
