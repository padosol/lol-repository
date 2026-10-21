package com.mmrtr.lol.controller.support.response;

import com.mmrtr.lol.support.error.ErrorMessage;
import com.mmrtr.lol.support.error.ErrorType;
import lombok.Getter;

/**
 * 공개 API 응답 래퍼. lol-server {@code ApiResponse<T>} 와 필드·직렬화 형태가 같아야 한다
 * ({@code result}, {@code data}, {@code errorMessage}).
 */
@Getter
public class ApiResponse<T> {
    private ResultType result;
    private T data;
    private ErrorMessage errorMessage;

    private ApiResponse() {}

    public ApiResponse(ResultType result, T data, ErrorMessage errorMessage) {
        this.result = result;
        this.data = data;
        this.errorMessage = errorMessage;
    }

    public static ApiResponse<?> success() {
        return new ApiResponse<>(ResultType.SUCCESS, null, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ResultType.SUCCESS, data, null);
    }

    public static <T> ApiResponse<T> error(ErrorType errorType) {
        return new ApiResponse<>(ResultType.ERROR, null, new ErrorMessage(errorType));
    }
}
