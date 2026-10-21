package com.mmrtr.lol.support.error;

import lombok.Getter;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

/**
 * 공개 API 로 나가는 타입은 lol-server {@code ErrorType} 과 이름·상태·코드·메시지를 맞춘다.
 * 리소스를 lol-server 에서 옮겨 와도 lol-ui 가 받는 에러 응답이 바뀌지 않아야 한다.
 */
@Getter
public enum ErrorType {
    DEFAULT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "알 수 없는 오류가 발생했습니다.", LogLevel.ERROR),

    EXTERNAL_API_ERROR(HttpStatus.BAD_REQUEST, ErrorCode.E400, "외부 API 호출 에러", LogLevel.WARN),

    NOT_FOUND_API(HttpStatus.NOT_FOUND, ErrorCode.E404, "존재하지 않는 API 입니다.", LogLevel.INFO),
    NOT_FOUND_PUUID(HttpStatus.NOT_FOUND, ErrorCode.E404, "존재하지 않는 PUUID 입니다.", LogLevel.INFO),
    // 저장된 platform_id 가 비었거나 모르는 값이라 Riot 을 어느 지역으로 부를지 정할 수 없다
    UNKNOWN_SUMMONER_PLATFORM(HttpStatus.NOT_FOUND, ErrorCode.E404, "플랫폼 정보가 없는 소환사입니다.", LogLevel.WARN),
    NOT_FOUND_MATCH_ID(HttpStatus.NOT_FOUND, ErrorCode.E404, "존재하지 않는 MatchId 입니다.", LogLevel.INFO),
    NOT_FOUND_USER(HttpStatus.NOT_FOUND, ErrorCode.E404, "존재하지 않는 유저 입니다.", LogLevel.INFO),
    NOT_FOUND_PATCH_NOTE(HttpStatus.NOT_FOUND, ErrorCode.E404, "존재하지 않는 패치노트 입니다.", LogLevel.INFO),
    NOT_FOUND_TIER_CUTOFF(HttpStatus.NOT_FOUND, ErrorCode.E404, "존재하지 않는 티어 컷오프입니다.", LogLevel.INFO),

    INVALID_INPUT(HttpStatus.BAD_REQUEST, ErrorCode.E400, "잘못된 입력값입니다.", LogLevel.INFO),
    INVALID_TIER_FILTER(HttpStatus.BAD_REQUEST, ErrorCode.E400, "유효하지 않은 티어 필터입니다.", LogLevel.INFO),
    // 전적 데이터가 적재된 적 없는 계정과 실제 언랭 계정은 사용자가 취할 행동이 다르므로 구분한다.
    SUMMONER_SEARCH_REQUIRED(HttpStatus.BAD_REQUEST, ErrorCode.E400,
            "전적 정보가 없습니다. 소환사 검색을 먼저 진행해주세요.", LogLevel.INFO),

    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.E429, "잠시 후 다시 실행해주세요.", LogLevel.WARN),
    LOCK_ACQUISITION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.E503, "잠시 후 다시 시도해주세요.", LogLevel.WARN)
    ;

    private final HttpStatus httpStatus;
    private final ErrorCode errorCode;
    private final String message;
    private final LogLevel logLevel;

    ErrorType(HttpStatus httpStatus, ErrorCode errorCode, String message, LogLevel logLevel) {
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.message = message;
        this.logLevel = logLevel;
    }
}
