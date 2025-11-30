package lol.mmrtr.lolrepository.support.error;

import lombok.Getter;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorType {
    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "알 수 없는 오류가 발생했습니다.", LogLevel.ERROR),
    NOT_FOUND_USER(HttpStatus.NOT_FOUND, ErrorCode.E404, "존재하지 않는 유저 입니다.", LogLevel.INFO)
    ;

    private HttpStatus httpStatus;
    private ErrorCode errorCode;
    private String message;
    private LogLevel logLevel;

    ErrorType(HttpStatus httpStatus, ErrorCode errorCode, String message, LogLevel logLevel) {
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.message = message;
        this.logLevel = logLevel;
    }
}
